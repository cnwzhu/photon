package de.komoot.photon.searcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.komoot.photon.opensearch.DocFields;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/** Formats reverse geocoding results using the shape of Baidu's reverse-geocoding API. */
@NullMarked
public class BaiduResultFormatter implements ResultFormatter {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String formatError(@Nullable String msg) {
        try {
            return mapper.writeValueAsString(Map.of("status", 1, "message", msg == null ? "Unknown error." : msg));
        } catch (JsonProcessingException e) {
            return "{\"status\":1}";
        }
    }

    @Override
    public String convert(List<PhotonResult> results, String language,
                          @Nullable String fallbackLanguage,
                          boolean withGeometry, boolean withDebugInfo,
                          @Nullable String queryDebugInfo) throws IOException {
        final var response = new LinkedHashMap<String, Object>();
        response.put("status", 0);

        final var reverseResult = new LinkedHashMap<String, Object>();
        final var pois = new ArrayList<Map<String, Object>>();
        if (!results.isEmpty()) {
            final var result = results.get(0);
            final var coords = result.getCoordinates();
            reverseResult.put("location", Map.of("lng", coords[0], "lat", coords[1]));
            reverseResult.put("formatted_address", formattedAddress(result, language, fallbackLanguage));
            reverseResult.put("addressComponent", addressComponent(result, language, fallbackLanguage));
            for (var poiResult : results) {
                pois.add(poi(poiResult, language, fallbackLanguage));
            }
        } else {
            reverseResult.put("location", Map.of("lng", 0, "lat", 0));
            reverseResult.put("formatted_address", "");
            reverseResult.put("addressComponent", emptyAddressComponent());
        }

        reverseResult.put("edz", Map.of("name", ""));
        reverseResult.put("business", "");
        reverseResult.put("business_info", List.of());
        reverseResult.put("pois", pois);
        reverseResult.put("roads", List.of());
        reverseResult.put("poiRegions", List.of());
        reverseResult.put("sematic_description", "");
        reverseResult.put("formatted_address_poi", "");
        reverseResult.put("cityCode", 0);
        response.put("result", reverseResult);
        return mapper.writeValueAsString(response);
    }

    private Map<String, Object> poi(PhotonResult result, String language, @Nullable String fallbackLanguage) {
        final var coords = result.getCoordinates();
        final var name = localized(result, DocFields.NAME, language, fallbackLanguage);
        final var osmKey = value(result, DocFields.OSM_KEY);
        final var osmValue = value(result, DocFields.OSM_VALUE);
        final var osmType = value(result, DocFields.OSM_TYPE);
        final var osmId = value(result, DocFields.OSM_ID);

        final var poi = new LinkedHashMap<String, Object>();
        final var aliasName = result.getAlternativeName(DocFields.NAME, name, "alt", "int", "loc", "reg", "old");
        poi.put("addr", formattedAddress(result, language, fallbackLanguage));
        poi.put("cp", " ");
        poi.put("direction", "");
        poi.put("distance", "");
        poi.put("name", name);
        poi.put("poiType", osmValue);
        poi.put("point", Map.of("x", coords[0], "y", coords[1]));
        poi.put("tag", osmKey.isEmpty() ? osmValue : osmKey + ";" + osmValue);
        poi.put("tel", "");
        poi.put("uid", osmType.isEmpty() || osmId.isEmpty() ? "" : osmType + "/" + osmId);
        poi.put("zip", value(result, DocFields.POSTCODE));
        poi.put("popularity_level", "1");
        poi.put("aoi_name", "");
        poi.put("alias_name", aliasName == null ? "" : aliasName);
        poi.put("parent_poi", emptyParentPoi());
        return poi;
    }

    private Map<String, Object> addressComponent(PhotonResult result, String language,
                                                  @Nullable String fallbackLanguage) {
        final var address = new LinkedHashMap<String, Object>();
        address.put("country", localized(result, DocFields.COUNTRY, language, fallbackLanguage));
        final var countryCode = value(result, DocFields.COUNTRYCODE).toUpperCase();
        final var countryCodeIso3 = iso3CountryCode(countryCode);
        address.put("region_code_iso", countryCodeIso3);
        address.put("country_code_iso", countryCodeIso3);
        address.put("country_code_iso2", countryCode);
        address.put("province", localized(result, DocFields.STATE, language, fallbackLanguage));
        address.put("city", localized(result, DocFields.CITY, language, fallbackLanguage));
        address.put("district", localized(result, DocFields.DISTRICT, language, fallbackLanguage));
        address.put("town", localized(result, DocFields.LOCALITY, language, fallbackLanguage));
        address.put("town_code", "");
        address.put("distance", "");
        address.put("direction", "");
        address.put("adcode", "0");
        address.put("street", localized(result, DocFields.STREET, language, fallbackLanguage));
        address.put("street_number", value(result, DocFields.HOUSENUMBER));
        return address;
    }

    private Map<String, Object> emptyAddressComponent() {
        final var address = new LinkedHashMap<String, Object>();
        for (var key : List.of("country", "region_code_iso", "country_code_iso", "country_code_iso2",
                "province", "city", "district", "town", "town_code", "distance", "direction",
                "adcode", "street", "street_number")) {
            address.put(key, "");
        }
        return address;
    }

    private Map<String, Object> emptyParentPoi() {
        return Map.of("name", "", "tag", "", "addr", "", "point", Map.of("x", 0, "y", 0),
                "direction", "", "distance", "", "uid", "", "popularity_level", "");
    }

    private String formattedAddress(PhotonResult result, String language, @Nullable String fallbackLanguage) {
        final var address = new StringJoiner(", ");
        for (var part : List.of(
                localized(result, DocFields.COUNTRY, language, fallbackLanguage),
                localized(result, DocFields.STATE, language, fallbackLanguage),
                localized(result, DocFields.CITY, language, fallbackLanguage),
                localized(result, DocFields.DISTRICT, language, fallbackLanguage),
                localized(result, DocFields.STREET, language, fallbackLanguage),
                value(result, DocFields.HOUSENUMBER))) {
            if (!part.isBlank()) {
                address.add(part);
            }
        }
        return address.toString();
    }

    private String localized(PhotonResult result, String key, String language, @Nullable String fallbackLanguage) {
        final var value = result.getLocalisedWithFallback(key, language, fallbackLanguage);
        return value == null ? "" : value;
    }

    private String value(PhotonResult result, String key) {
        final var value = result.get(key);
        return value == null ? "" : value.toString();
    }

    private String iso3CountryCode(String countryCode) {
        if (countryCode.length() != 2) {
            return "";
        }
        try {
            return new Locale.Builder().setRegion(countryCode).build().getISO3Country();
        } catch (java.util.IllformedLocaleException | java.util.MissingResourceException e) {
            return "";
        }
    }
}
