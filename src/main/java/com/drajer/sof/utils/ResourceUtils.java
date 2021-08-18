package com.drajer.sof.utils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hl7.fhir.instance.model.api.IBaseResource;

public class ResourceUtils {

    public static <T extends IBaseResource> List<T> deduplicate(List<T> resources) {
        Map<String, List<T>> groupedById = resources.stream().collect(Collectors.groupingBy(
                x -> x.getIdElement().getResourceType() + "/" + x.getIdElement().getIdPart(), Collectors.toList()));

        List<T> sorted = groupedById.entrySet().stream()
                .map(x -> x.getValue().stream()
                        .sorted(Comparator.comparingInt(y -> y.getMeta() != null && y.getMeta().getVersionId() != null ? Integer.parseInt(y.getMeta().getVersionId()) : 0)).findFirst()
                        .get())
                .collect(Collectors.toList());

        return sorted;
    }
}
