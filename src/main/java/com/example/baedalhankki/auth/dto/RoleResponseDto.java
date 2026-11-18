package com.example.baedalhankki.auth.dto;

import com.example.baedalhankki.member.entity.MemberRole;

public record RoleResponseDto(MemberRole role) {
    public static RoleResponseDto of(MemberRole role) {
        return new RoleResponseDto(role);
    }
}
