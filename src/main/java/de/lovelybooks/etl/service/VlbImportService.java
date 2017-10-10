package de.lovelybooks.etl.service;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tectonica.jonix.Onix3Extractor;
import com.tectonica.jonix.onix3.Product;
import com.tectonica.jonix.stream.JonixFilesStreamer;
import com.tectonica.jonix.stream.JonixStreamer;

import de.lovelybooks.entity.Edition;
import de.lovelybooks.model.ProductStatus;
import de.lovelybooks.etl.util.FtpFileProvider;
import de.lovelybooks.etl.util.ZippedFileInputStream;
import net.minidev.json.JSONArray;

@Service
public class VlbImportService {

    private static Logger log = LoggerFactory.getLogger(VlbImportService.class.getName());

    @Value("${vlb.file.prefix.new}")
    private String filePrefixNew;
    @Value("${vlb.file.prefix.delete}")
    private String filePrefixDelete;
    @Value("${vlb.file.prefix.update}")
    private String filePrefixUpdate;
    @Value("${vlb.file.patternWithActionGroup}")
    private String onixFilePattern;

    private Map<String, String> productMap;
    private Set<Edition> editions;
    private ProductStatus currentProductStatus;
    private Pattern onixFileCompilePattern;
    private Object tmpObject;
    private Object listObject;

    @Autowired
    private DataTranslationService dataTranslationService;

    @Autowired
    private FtpFileProvider ftpFileProvider;

    @Autowired
    private DataPersistenceService dataPersistenceService;

    public void start() {
        onixFileCompilePattern = Pattern.compile(onixFilePattern);
        ProductStatus.NEW.setPrefix(filePrefixNew);
        ProductStatus.UPDATED.setPrefix(filePrefixUpdate);
        ProductStatus.DELETED.setPrefix(filePrefixDelete);

        editions = new HashSet<>();

        ftpFileProvider.connect();
        ZipInputStream is = ftpFileProvider.openZipFile();
        try {
            productMap = dataTranslationService.getMap();

            JonixFilesStreamer streamer = new JonixFilesStreamer(new Onix3Extractor() {
                @Override
                protected boolean onProduct(Product product, JonixStreamer streamer) {
                    Edition edition = handleProduct(product);
                    if (edition != null) {
                        editions.add(edition);
                    }
                    return true;
                }
            });

            ZipEntry file = null;
            while (is != null && (file = is.getNextEntry()) != null) {
                ZippedFileInputStream fis = new ZippedFileInputStream(is);
                Matcher matcher = onixFileCompilePattern.matcher(file.getName());
                if (matcher.matches()) {
                    currentProductStatus = Arrays.stream(ProductStatus.values())
                            .filter(e -> e.getPrefix().equals(matcher.group(1))).findFirst()
                            .orElseThrow(
                                    () -> new IOException(String.format("Unsupported type %s.", matcher.group(1))));
                    log.info("Reading file " + file.getName() + ".");
                    streamer.read(fis);
                }
            }
            aggregateTitlesInWorks();
            dataPersistenceService.saveEditions(editions);
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        } finally {
            ftpFileProvider.disconnect(is);
        }

    }

    private Edition handleProduct(Product product) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String productJson = mapper.writeValueAsString(product);
            log.debug(productJson);
            final Edition edition = new Edition();
            if (dataTranslationService.includeProduct(productJson)) {
                productMap.forEach((prop, path) -> {
                    setNestedProperty(edition, prop, productJson, path);
                });
                Edition populatedEdition = dataPersistenceService.populateTitleFromDb(edition);
                populatedEdition.setProductStatus(currentProductStatus);
                logEditionObject(edition);
                return populatedEdition;
            } else {
                log.debug("Product blacklisted => skipping it.");
            }
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON.");
        }
        return null;
    }

    private void logEditionObject(final Edition edition) {
        log.debug("Edition Dump:");
        Arrays.asList(BeanUtils.getPropertyDescriptors(Edition.class)).forEach((pd) -> {
            try {
                if (pd.getReadMethod() != null && !"class".equals(pd.getName())) {
                    Object value = pd.getReadMethod().invoke(edition);
                    if (value != null) {
                        log.debug("{} => {}", pd.getName(), value);
                    }
                }
            } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                log.error("Reflection error: {}", e.getMessage());
            }
        });
    }

    private void setNestedProperty(Edition edition, String prop, String productJson, String path) {
        tmpObject = edition;
        String prevToken = null;
        Object prevObject = null;
        boolean isList = false;
        String[] tokens = prop.split("\\.");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            Object newTmpObject = null;
            try {
                if (i < tokens.length - 1) {
                    PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(tmpObject.getClass(), token);
                    newTmpObject = pd.getReadMethod().invoke(tmpObject);
                    if (newTmpObject == null) {
                        Type returnType = pd.getReadMethod().getReturnType();
                        if (List.class.isAssignableFrom((Class<?>) returnType)) {
                            isList = true;
                            Type genericType = ((ParameterizedType) pd.getReadMethod().getGenericReturnType())
                                    .getActualTypeArguments()[0];
                            newTmpObject = ((Class<?>) genericType).newInstance();
                            listObject = new ArrayList<>();
                            pd.getWriteMethod().invoke(tmpObject, listObject);
                        } else {
                            isList = false;
                            newTmpObject = pd.getPropertyType().newInstance();
                        }
                    }
                    prevObject = tmpObject;
                    tmpObject = newTmpObject;
                } else {
                    Object value = dataTranslationService.translate(productJson, tmpObject, token, path, isList);
                    if (value instanceof JSONArray) {
                        ((JSONArray) value).forEach(v -> {
                            try {
                                // Object o =
                                // tmpObject.getClass().newInstance();
                                BeanUtils.getPropertyDescriptor(tmpObject.getClass(), token).getWriteMethod()
                                        .invoke(tmpObject,
                                        v);
                                ((List<Object>) listObject).add(tmpObject);
                            } catch (BeansException | IllegalAccessException | IllegalArgumentException
                                    | InvocationTargetException e) {
                                log.error(e.getMessage());
                            }
                        });
                        BeanUtils.getPropertyDescriptor(prevObject.getClass(), prevToken).getWriteMethod()
                                .invoke(prevObject, listObject);
                    } else {
                        BeanUtils.getPropertyDescriptor(tmpObject.getClass(), token).getWriteMethod().invoke(tmpObject,
                                value);
                    }
                }
            } catch (InvocationTargetException | BeansException | IllegalAccessException | IllegalArgumentException
                    | InstantiationException e) {
                log.error(e.getMessage());
            }
            prevToken = token;
        }
    }

    private void aggregateTitlesInWorks() {

    }

}
