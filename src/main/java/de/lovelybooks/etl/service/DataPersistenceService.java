package de.lovelybooks.etl.service;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.lovelybooks.entity.Edition;
import de.lovelybooks.etl.repository.EditionRepository;

@Service
public class DataPersistenceService {

    private static Logger log = LoggerFactory.getLogger(DataPersistenceService.class.getName());

    @Autowired
    private EditionRepository editionRepository;

    public Edition populateTitleFromDb(Edition edition) {
        Edition existingEdition = editionRepository.findFirstByIsbn13(edition.getIsbn13());
        if (existingEdition != null) {
            Arrays.asList(BeanUtils.getPropertyDescriptors(Edition.class)).forEach((pd) -> {
                try {
                    if (pd.getReadMethod() != null) {
                        Object existingValue = pd.getReadMethod().invoke(existingEdition);
                        if (existingValue != null && pd.getWriteMethod() != null) {
                            pd.getWriteMethod().invoke(edition, existingValue);
                            log.info("Overwrite title field {} with {}", pd.getName(), existingValue.toString());
                        }
                    }
                } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                    log.error("Reflection error: {}", e.getMessage());
                }
            });
        }
        return edition;
    }

    public void saveEditions(Set<Edition> editions) {
        editionRepository.save(editions);
    }

}
