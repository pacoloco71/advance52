package de.lovelybooks.etl.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;

import net.minidev.json.JSONArray;

@Service
@ConfigurationProperties("vlb.product")
public class DataTranslationService {

    private static Logger log = LoggerFactory.getLogger(DataTranslationService.class.getName());

    private Map<String, Map<String, String>> mapper = new HashMap<>();
    
    private List<String> blacklist = new ArrayList<>();
    private List<String> whitelist = new ArrayList<>();
    private Map<String, String> map = new HashMap<>();

    public Object translate(String productJson, Object type, String field, String jsonPath, boolean mayReturnArray) {
        Object value = parseOnixExpression(productJson, jsonPath, mayReturnArray);
        if (value != null && !(value instanceof JSONArray)) {
            value = mapFieldValue(field, String.valueOf(value));
            value = autoCastValue(type, field, value);
        }
        return value;
    }

    public boolean includeProduct(String productJson) {
        return blacklist.stream().noneMatch(exp -> parseOnixExpression(productJson, exp, false) != null)
                || whitelist.stream().anyMatch(exp -> parseOnixExpression(productJson, exp, false) != null);
    }

    private Object mapFieldValue(String field, String value) {
        Map<String, String> fieldMap = mapper.get(field);
        return (fieldMap != null && fieldMap.containsKey(value)) ? fieldMap.get(value) : value;
    }

    private Object parseOnixExpression(String productJson, String jsonPath, boolean mayReturnArray) {
        Object value = null;
        try {
            value = JsonPath.read(productJson, jsonPath);
            if (mayReturnArray && value instanceof JSONArray) {
                return value;
            } else if (value instanceof List) {
                value = (((List<?>) value).size() > 0) ? value = ((List<?>) value).get(0) : null;
            }
        } catch (InvalidPathException e) {
            log.warn(e.getMessage());
        }
        return value;
    }

    private Object autoCastValue(Object type, String field, Object value) {
        Class<?> fieldType = BeanUtils.getPropertyDescriptor(type.getClass(), field).getPropertyType();
        if (fieldType == Integer.class) {
            if (value instanceof String) {
                value = Double.valueOf((String) value).intValue();
            }
        }
        return value;
    }

    public List<String> getBlacklist() {
        return blacklist;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public Map<String, String> getMap() {
        return map;
    }

    public Map<String, Map<String, String>> getMapper() {
        return mapper;
    }
}
