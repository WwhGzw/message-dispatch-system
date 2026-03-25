package com.msg.access.controller;

import com.msg.access.dto.ReceiptCallback;
import com.msg.access.service.MessageService;
import com.msg.common.dto.CancelRequest;
import com.msg.common.dto.CancelResult;
import com.msg.common.dto.DelaySendRequest;
import com.msg.common.dto.MessageStatusVO;
import com.msg.common.dto.SendRequest;
import com.msg.common.dto.SendResult;
import com.msg.common.dto.StatusQuery;
import com.msg.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 消息接入服务 Controller
 * 提供即时下发、延迟下发、状态查询、消息撤回、渠道回执回调接口
 */
@RestController
@RequestMapping("/msg")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 即时消息下发
     */
    @PostMapping("/send/now")
    public Result<SendResult> sendNow(@RequestBody @Valid SendRequest request) {
        SendResult result = messageService.sendNow(request);
        return Result.ok(result);
    }

    /**
     * 延迟/定时消息下发
     */
    @PostMapping("/send/delay")
    public Result<SendResult> sendDelay(@RequestBody @Valid DelaySendRequest request) {
        SendResult result = messageService.sendDelay(request);
        return Result.ok(result);
    }

    /**
     * 消息状态查询
     */
    @GetMapping("/status")
    public Result<MessageStatusVO> queryStatus(@Valid StatusQuery query) {
        MessageStatusVO status = messageService.queryStatus(query);
        return Result.ok(status);
    }

    /**
     * 消息撤回
     */
    @PostMapping("/cancel")
    public Result<CancelResult> cancel(@RequestBody @Valid CancelRequest request) {
        CancelResult result = messageService.cancel(request);
        return Result.ok(result);
    }

    /**
     * 渠道回执回调
     */
    @PostMapping("/callback/receipt")
    public Result<Void> handleReceipt(@RequestBody ReceiptCallback receipt) {
        messageService.handleReceipt(receipt);
        return Result.ok();
    }
}
