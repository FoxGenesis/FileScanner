package net.foxgenesis.filescanner.cascade;

import java.util.function.Consumer;

import net.foxgenesis.filescanner.cascade.haar.HaarCascade;

public record CascadeEntry(HaarCascade cascade, Consumer<CascadeDetectionData> consumer) {

}