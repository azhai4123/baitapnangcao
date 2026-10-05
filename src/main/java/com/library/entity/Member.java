package com.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false, length = 254, unique = true)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 500)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Version
    @Column(nullable = false)
    private int version;

    public Member(String fullName, String email, MemberStatus status) {
        this.fullName = fullName;
        this.email = email;
        this.status = status;
    }

    public boolean canBorrow() {
        return status == MemberStatus.ACTIVE;
    }
}
