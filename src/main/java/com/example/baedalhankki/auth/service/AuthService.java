package com.example.baedalhankki.auth.service;

import com.example.baedalhankki.auth.dto.SignupRequestDto;
import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.global.exception.ErrorCode;
import com.example.baedalhankki.global.security.AuthMember;
import com.example.baedalhankki.member.repository.MemberRepository;
import com.example.baedalhankki.member.entity.Member;
import com.example.baedalhankki.member.entity.MemberRole;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;

@Service
public class AuthService {
    private static final int MAX_PASSWORD_BYTES = 72;

    private final MemberRepository members;
    private final PasswordEncoder encoder;
    private final String dummyPasswordHash;
    private final TransactionTemplate signupTransaction;

    public AuthService(MemberRepository members, PasswordEncoder encoder,
            PlatformTransactionManager transactionManager) {
        this.members = members;
        this.encoder = encoder;
        dummyPasswordHash = encoder.encode("dummyPasswordHash-password");
        signupTransaction = new TransactionTemplate(transactionManager);
    }

    public void signup(SignupRequestDto request) {
        try {
            signupTransaction.executeWithoutResult(status -> {
                if (members.existsByLoginId(request.loginId())) {
                    throw new ApiException(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
                }
                members.saveAndFlush(Member.create(request.loginId(), encoder.encode(request.password()),
                        request.name(), MemberRole.CUSTOMER));
            });
        } catch (DataIntegrityViolationException exception) {
            // 중복 저장 여부는 실패한 트랜잭션이 롤백된 뒤 조회한다.
            if (members.existsByLoginId(request.loginId())) {
                throw new ApiException(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public AuthMember login(String loginId, String password) {
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(password)
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        var member = members.findByLoginId(loginId);
        boolean passwordMatches = encoder.matches(password, member.map(v -> v.getPasswordHash()).orElse(dummyPasswordHash));
        if (member.isEmpty() || !passwordMatches) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        return new AuthMember(member.get().getId(), member.get().getRole());
    }
}
