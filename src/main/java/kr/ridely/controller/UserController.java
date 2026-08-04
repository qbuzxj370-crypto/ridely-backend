package kr.ridely.controller;

import jakarta.validation.Valid;
import kr.ridely.common.ApiResponse;
import kr.ridely.dto.user.UserResponseDTO;
import kr.ridely.dto.user.UserUpdateRequestDTO;
import kr.ridely.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 정보 컨트롤러. 인증 필요.
 *
 * GET   /api/v1/users/me  내 정보 조회
 * PATCH /api/v1/users/me  내 정보 수정
 *
 * 회원 번호는 경로나 파라미터로 받지 않고 토큰에서 꺼낸다.
 * 클라이언트가 보낸 값을 쓰면 번호만 바꿔 남의 정보에 접근할 수 있다.
 * JwtAuthenticationFilter가 인증 정보에 회원 번호를 넣어 두므로
 * @AuthenticationPrincipal로 바로 받는다.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 내 정보 조회 */
    @GetMapping("/me")
    public ApiResponse<UserResponseDTO> me(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.findById(userId));
    }

    /**
     * 내 정보 수정.
     * 보낸 항목만 바뀐다. 아이디는 변경 불가, 비밀번호는 별도 API로 분리 예정.
     */
    @PatchMapping("/me")
    public ApiResponse<UserResponseDTO> updateMe(@AuthenticationPrincipal Long userId,
                                                 @Valid @RequestBody UserUpdateRequestDTO request) {
        return ApiResponse.ok(userService.updateProfile(userId, request));
    }
}
