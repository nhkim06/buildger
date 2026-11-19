package com.example.backend.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * storage 테이블 (create_table.sql 4. STORAGE). 생성 후 수정하지 않는다.
 * 형식·크기·해시·소속 기록은 data.metadata에 저장한다. (docs/api.md 3.4)
 */
@Entity
@Table(name = "storage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Storage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "storage_id", nullable = false, updatable = false)
    private Long storageId;

    @Column(name = "name", updatable = false)
    private String name;

    @Column(name = "image", nullable = false, updatable = false)
    private byte[] image;

    public Storage(String name, byte[] image) {
        this.name = name;
        this.image = image;
    }
}
