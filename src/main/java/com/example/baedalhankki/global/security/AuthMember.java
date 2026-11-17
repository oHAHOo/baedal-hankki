package com.example.baedalhankki.global.security;

import com.example.baedalhankki.member.entity.MemberRole;

import java.io.Serializable;

public record AuthMember(Long memberId, MemberRole role) implements Serializable {}
