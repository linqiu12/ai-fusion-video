package com.stonewu.fusion.controller.system;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.controller.system.vo.LoginReqVO;
import com.stonewu.fusion.security.TokenService;
import com.stonewu.fusion.service.system.MailService;
import com.stonewu.fusion.service.system.SystemConfigService;
import com.stonewu.fusion.service.system.UserService;
import com.stonewu.fusion.service.team.TeamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTests {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenService tokenService;

    @Mock
    private UserService userService;

    @Mock
    private TeamService teamService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private MailService mailService;

    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private AuthController authController;

    @Test
    void loginShouldReportUserNotFoundBeforeAuthenticating() {
        LoginReqVO request = new LoginReqVO();
        request.setUsername("missing-user");
        request.setPassword("secret123");
        when(userService.getByUsername("missing-user")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authController.login(request));

        assertEquals(404, exception.getCode());
        assertEquals("用户不存在", exception.getMessage());
    }
}
