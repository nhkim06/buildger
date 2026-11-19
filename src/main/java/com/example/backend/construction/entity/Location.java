package com.example.backend.construction.entity;

import com.example.backend.global.jsonb.JsonbObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * data.metadata.location
 */
@Getter
@Setter
@NoArgsConstructor
public class Location extends JsonbObject {

    private String site;
    private String building;
    private String floor;
    private String zone;
    private String address;
    private Double latitude;
    private Double longitude;
}
