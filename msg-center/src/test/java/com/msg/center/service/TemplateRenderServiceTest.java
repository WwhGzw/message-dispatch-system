package com.msg.center.service;

import com.msg.center.exception.TemplateRenderException;
import com.msg.common.entity.MessageTemplateEntity;
import com.msg.common.mapper.MessageTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TemplateRenderService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TemplateRenderServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MessageTemplateMapper templateMapper;

    @InjectMocks
    private TemplateRenderService templateRenderService;

    private static final String TEMPLATE_CODE = "order_shipped";
    private static final String CACHE_KEY = "tpl:order_shipped";
    private static final String TEMPLATE_CONTENT = "您的订单 ${orderNo} 已发货，快递单号 ${expressNo}";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Trigger @PostConstruct manually since Mockito doesn't call it
        templateRenderService.init();
    }

    // ========== 成功渲染 ==========

    @Test
    void renderTemplate_cacheHit_returnsRenderedContent() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(TEMPLATE_CONTENT);

        Map<String, Object> variables = new HashMap<>();
        variables.put("orderNo", "ORD-001");
        variables.put("expressNo", "SF123456");

        String result = templateRenderService.renderTemplate(TEMPLATE_CODE, variables);

        assertEquals("您的订单 ORD-001 已发货，快递单号 SF123456", result);
        verify(templateMapper, never()).selectByTemplateCode(anyString());
    }

    @Test
    void renderTemplate_cacheMiss_loadsFromDbAndCaches() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        MessageTemplateEntity entity = MessageTemplateEntity.builder()
                .templateCode(TEMPLATE_CODE)
                .content(TEMPLATE_CONTENT)
                .enabled(true)
                .build();
        when(templateMapper.selectByTemplateCode(TEMPLATE_CODE)).thenReturn(entity);

        Map<String, Object> variables = new HashMap<>();
        variables.put("orderNo", "ORD-002");
        variables.put("expressNo", "YT789012");

        String result = templateRenderService.renderTemplate(TEMPLATE_CODE, variables);

        assertEquals("您的订单 ORD-002 已发货，快递单号 YT789012", result);
        verify(valueOperations).set(eq(CACHE_KEY), eq(TEMPLATE_CONTENT), eq(24L), eq(TimeUnit.HOURS));
    }

    // ========== 模板不存在 ==========

    @Test
    void renderTemplate_templateNotFound_throwsException() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(templateMapper.selectByTemplateCode(TEMPLATE_CODE)).thenReturn(null);

        TemplateRenderException ex = assertThrows(TemplateRenderException.class,
                () -> templateRenderService.renderTemplate(TEMPLATE_CODE, Map.of()));

        assertTrue(ex.getMessage().contains("模板不存在"));
    }

    // ========== 模板未启用 ==========

    @Test
    void renderTemplate_templateDisabled_throwsException() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        MessageTemplateEntity entity = MessageTemplateEntity.builder()
                .templateCode(TEMPLATE_CODE)
                .content(TEMPLATE_CONTENT)
                .enabled(false)
                .build();
        when(templateMapper.selectByTemplateCode(TEMPLATE_CODE)).thenReturn(entity);

        TemplateRenderException ex = assertThrows(TemplateRenderException.class,
                () -> templateRenderService.renderTemplate(TEMPLATE_CODE, Map.of()));

        assertTrue(ex.getMessage().contains("模板未启用"));
    }

    // ========== 变量缺失 ==========

    @Test
    void renderTemplate_missingVariable_throwsException() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(TEMPLATE_CONTENT);

        // Only provide one of two required variables
        Map<String, Object> variables = new HashMap<>();
        variables.put("orderNo", "ORD-003");

        TemplateRenderException ex = assertThrows(TemplateRenderException.class,
                () -> templateRenderService.renderTemplate(TEMPLATE_CODE, variables));

        assertTrue(ex.getMessage().contains("模板渲染失败"));
    }

    // ========== 未替换占位符检测 ==========

    @Test
    void renderTemplate_unreplacedPlaceholder_throwsException() {
        // Template that produces literal ${...} in output (e.g. escaped or nested)
        String tplWithLiteral = "结果: ${r\"${unreplaced}\"}";
        when(valueOperations.get(CACHE_KEY)).thenReturn(tplWithLiteral);

        Map<String, Object> variables = new HashMap<>();

        TemplateRenderException ex = assertThrows(TemplateRenderException.class,
                () -> templateRenderService.renderTemplate(TEMPLATE_CODE, variables));

        assertTrue(ex.getMessage().contains("未替换的占位符"));
    }

    // ========== Redis 不可用降级 ==========

    @Test
    void renderTemplate_redisUnavailable_fallsBackToDb() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));
        MessageTemplateEntity entity = MessageTemplateEntity.builder()
                .templateCode(TEMPLATE_CODE)
                .content(TEMPLATE_CONTENT)
                .enabled(true)
                .build();
        when(templateMapper.selectByTemplateCode(TEMPLATE_CODE)).thenReturn(entity);

        Map<String, Object> variables = new HashMap<>();
        variables.put("orderNo", "ORD-004");
        variables.put("expressNo", "ZT345678");

        String result = templateRenderService.renderTemplate(TEMPLATE_CODE, variables);

        assertEquals("您的订单 ORD-004 已发货，快递单号 ZT345678", result);
    }

    // ========== Redis 缓存写入失败不影响渲染 ==========

    @Test
    void renderTemplate_redisCacheWriteFails_stillReturnsResult() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        MessageTemplateEntity entity = MessageTemplateEntity.builder()
                .templateCode(TEMPLATE_CODE)
                .content(TEMPLATE_CONTENT)
                .enabled(true)
                .build();
        when(templateMapper.selectByTemplateCode(TEMPLATE_CODE)).thenReturn(entity);
        doThrow(new RuntimeException("Redis write failed"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        Map<String, Object> variables = new HashMap<>();
        variables.put("orderNo", "ORD-005");
        variables.put("expressNo", "EMS999");

        String result = templateRenderService.renderTemplate(TEMPLATE_CODE, variables);

        assertEquals("您的订单 ORD-005 已发货，快递单号 EMS999", result);
    }
}
