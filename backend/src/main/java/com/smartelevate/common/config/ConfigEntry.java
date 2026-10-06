package com.smartelevate.common.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Una fila de {@code app_config}. Se lee solo a través de {@link ConfigService}. */
@Entity
@Table(name = "app_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ConfigEntry {

    @Id
    @Column(name = "config_key")
    private String key;

    @Column(name = "config_value")
    private String value;
}
