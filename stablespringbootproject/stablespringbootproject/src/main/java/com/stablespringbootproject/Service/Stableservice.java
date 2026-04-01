package com.stablespringbootproject.Service;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stablespringbootproject.Dto.Stablerequest;
import com.stablespringbootproject.Dto.Stableresponse;
import com.stablespringbootproject.Entity.Countryentity;
import com.stablespringbootproject.Entity.Countryserviceentity;
import com.stablespringbootproject.Entity.EngineInfo;
import com.stablespringbootproject.Entity.Vehicleresponcemapping;
import com.stablespringbootproject.Entity.Vendorapis;
import com.stablespringbootproject.Entity.Vendorentity;
import com.stablespringbootproject.Entity.vehiclerequestmapping;
import com.stablespringbootproject.repository.Countryrepo;
import com.stablespringbootproject.repository.Countryservicerepo;
import com.stablespringbootproject.repository.Vehiclerequestmappingrepo;
import com.stablespringbootproject.repository.VendorJsonMappingrepo;
import com.stablespringbootproject.repository.Vendorapirepo;
import com.stablespringbootproject.repository.Vendorrepo;

@Service
public class Stableservice {

    private final RestTemplate restTemplate;
    private final Countryrepo countryRepo;
    private final Countryservicerepo stableRepo;
    private final Vendorrepo vendorRepo;
    private final Vendorapirepo vendorApiRepository;
    private final VendorJsonMappingrepo jsonMappingRepo;
    private final Vehiclerequestmappingrepo vehicleRequestMappingRepo;

    // ✅ EngineService injected — no more EngineinfoRepository here
    private final EngineService engineService;

    public Stableservice(RestTemplate restTemplate,
                         Countryrepo countryRepo,
                         Countryservicerepo stableRepo,
                         Vendorrepo vendorRepo,
                         Vendorapirepo vendorApiRepository,
                         VendorJsonMappingrepo jsonMappingRepo,
                         Vehiclerequestmappingrepo vehicleRequestMappingRepo,
                         EngineService engineService) {

        this.restTemplate = restTemplate;
        this.countryRepo = countryRepo;
        this.stableRepo = stableRepo;
        this.vendorRepo = vendorRepo;
        this.vendorApiRepository = vendorApiRepository;
        this.jsonMappingRepo = jsonMappingRepo;
        this.vehicleRequestMappingRepo = vehicleRequestMappingRepo;
        this.engineService = engineService;  // ✅ replaced engineRepo
    }

    // =====================================================
    // MAIN ENTRY
    // =====================================================
    public Stableresponse fetchVehicle(Stablerequest request) {

        // 🔥 ENGINE FLOW
        if ("ENGINE".equalsIgnoreCase(request.getApi_usage_type())) {
            return getEngineDetails(request);
        }

        // ===== VEHICLE (VENDOR API FLOW) =====

        Countryentity country = countryRepo.findByCountryCode(request.getCountry())
                .orElseThrow(() -> new RuntimeException("Country Not Found"));

        Countryserviceentity service = stableRepo
                .findFirstByCountryCodeAndActiveTrue(country.getCountryCode())
                .orElseThrow(() -> new RuntimeException("No active service found"));

        Vendorentity vendor = vendorRepo.findByVendorNameIgnoreCaseAndPhoneNumber(
                request.getVendorname(), request.getPhone_number());

        if (vendor == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor Not Found");
        }

        List<Vendorapis> vendorApiList = vendorApiRepository
                .findByVendorIdAndApiType(vendor.getId(), request.getApi_usage_type());

        for (Vendorapis vendorApi : vendorApiList) {

            List<vehiclerequestmapping> requestMappings =
                    vehicleRequestMappingRepo.findByVendorIdAndApiId(
                            vendor.getId(), vendorApi.getApiId());

            Map<String, Object> rawResponse =
                    callVendor(service, vendorApi, requestMappings, request);

            if (rawResponse != null) {

                List<Vehicleresponcemapping> mappings =
                        jsonMappingRepo.findByApiId(vendorApi.getApiId());

                Stableresponse response =
                        mapVendorResponse(rawResponse, mappings);

                response.setCountry(country.getCountryCode());

                return response;
            }
        }

        throw new RuntimeException("Vehicle not found");
    }

    // =====================================================
    // 🔥 ENGINE STATIC FLOW
    // — Direct DB fetch via EngineService (no HTTP call)
    // — Filters by vehicleId + countryCode to avoid duplicates
    // =====================================================
    private Stableresponse getEngineDetails(Stablerequest request) {

        // ✅ Fetch by vehicleId + countryCode — handles duplicate vehicleIds
        EngineInfo engine = engineService.getEngineByVehicleAndCountry(
                request.getVehicleId(),
                request.getCountry()
        );

        // ✅ Static manual mapping — no DB config, no reflection needed
        Map<String, String> vehicleDetails = new HashMap<>();
        vehicleDetails.put("engineId",       engine.getEngineId() != null ? engine.getEngineId().toString() : null);
        vehicleDetails.put("vehicleId",      engine.getVehicleId() != null ? engine.getVehicleId().toString() : null);
        vehicleDetails.put("engineNumber",   engine.getEngineNumber());
        vehicleDetails.put("engineType",     engine.getEngineType());
        vehicleDetails.put("engineCapacity", engine.getEngineCapacity() != null ? engine.getEngineCapacity().toString() : null);
        vehicleDetails.put("fuelType",       engine.getFuelType());
        vehicleDetails.put("extraData",      engine.getExtraData());
        vehicleDetails.put("countryCode",    engine.getCountryCode());

        vehicleDetails.values().removeIf(v -> v == null); // clean nulls

        Stableresponse response = new Stableresponse();
        response.setVehicleDetails(vehicleDetails);
        response.setCountry(request.getCountry());

        return response;
    }

    // =====================================================
    // CALL VENDOR API
    // =====================================================
    private Map<String, Object> callVendor(Countryserviceentity service,
                                           Vendorapis vendorApi,
                                           List<vehiclerequestmapping> requestMappings,
                                           Stablerequest request) {

        String fullUrl = service.getBaseUrl() + vendorApi.getApiUrl();

        Map<String, String> requestMap = convertRequestToMap(request);

        Map<String, String> pathVars = new HashMap<>();
        Map<String, String> queryParams = new HashMap<>();
        Map<String, String> headersMap = new HashMap<>();
        Map<String, Object> body = new HashMap<>();

        for (vehiclerequestmapping mapping : requestMappings) {

            String value = mapping.getConstantValue() != null &&
                    !mapping.getConstantValue().isEmpty()
                    ? mapping.getConstantValue()
                    : getIgnoreCase(requestMap, mapping.getStableField());

            if (value == null) continue;

            switch (mapping.getLocation()) {
                case PATH -> pathVars.put(mapping.getExternalName(), value);
                case QUERY -> queryParams.put(mapping.getExternalName(), value);
                case HEADER -> headersMap.put(mapping.getExternalName(), value);
                case BODY_JSON -> body.put(mapping.getExternalName(), value);
            }
        }

        fullUrl = resolveUrl(fullUrl, pathVars);

        if (!queryParams.isEmpty()) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(fullUrl);
            queryParams.forEach(builder::queryParam);
            fullUrl = builder.toUriString();
        }

        HttpHeaders headers = new HttpHeaders();
        headersMap.forEach(headers::add);

        HttpMethod method = HttpMethod.valueOf(vendorApi.getHttpMethod().toUpperCase());

        HttpEntity<?> entity = method == HttpMethod.GET
                ? new HttpEntity<>(headers)
                : new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
                restTemplate.exchange(URI.create(fullUrl), method, entity, Map.class);

        return response.getBody();
    }

    // =====================================================
    // UTIL METHODS (UNCHANGED)
    // =====================================================

    private String resolveUrl(String url, Map<String, String> vars) {

        Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(url);

        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = getIgnoreCase(vars, key);

            matcher.appendReplacement(result,
                    URLEncoder.encode(value, StandardCharsets.UTF_8));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private Stableresponse mapVendorResponse(Map<String, Object> rawData,
                                             List<Vehicleresponcemapping> mappings) {

        Stableresponse response = new Stableresponse();
        Map<String, String> result = new HashMap<>();

        if (mappings.isEmpty()) return response;

        Vehicleresponcemapping config = mappings.get(0);

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.convertValue(rawData, JsonNode.class);

            for (Field field : config.getClass().getDeclaredFields()) {

                if (List.of("id", "apiId", "vendorId", "countryId")
                        .contains(field.getName())) continue;

                field.setAccessible(true);

                Object vendorField = field.get(config);

                if (vendorField != null) {
                    String value = findValue(root, vendorField.toString());
                    if (value != null) {
                        result.put(field.getName(), value);
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        response.setVehicleDetails(result);
        return response;
    }

    private String findValue(JsonNode node, String key) {

        if (node == null) return null;

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();

                if (entry.getKey().equalsIgnoreCase(key)
                        && entry.getValue().isValueNode()) {
                    return entry.getValue().asText();
                }

                String val = findValue(entry.getValue(), key);
                if (val != null) return val;
            }
        }

        if (node.isArray()) {
            for (JsonNode n : node) {
                String val = findValue(n, key);
                if (val != null) return val;
            }
        }

        return null;
    }

    private Map<String, String> convertRequestToMap(Object obj) {

        Map<String, String> map = new HashMap<>();
        Field[] fields = obj.getClass().getDeclaredFields();

        try {
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(obj);

                if (value != null) {
                    map.put(field.getName(), value.toString());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return map;
    }

    private String getIgnoreCase(Map<String, String> map, String key) {
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return null;
    }
}