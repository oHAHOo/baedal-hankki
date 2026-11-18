package com.example.baedalhankki.auth.service;

import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.global.exception.ErrorCode;
import com.example.baedalhankki.global.security.AuthMember;
import com.example.baedalhankki.member.repository.MemberRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final MemberRepository members;
    private final PasswordEncoder encoder;
    private final String dummyPasswordHash;

    public AuthService(MemberRepository members, PasswordEncoder encoder) {
        this.members = members;
        this.encoder = encoder;
        dummyPasswordHash = encoder.encode("dummyPasswordHash-password");
    }

    @Transactional(readOnly = true)
    public AuthMember login(String loginId, String password) {
        var member = members.findByLoginId(loginId);
        boolean passwordMatches = encoder.matches(password, member.map(v -> v.getPasswordHash()).orElse(dummyPasswordHash));
        if (member.isEmpty() || !passwordMatches)
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        return new AuthMember(member.get().getId(), member.get().getRole());
    }
}
