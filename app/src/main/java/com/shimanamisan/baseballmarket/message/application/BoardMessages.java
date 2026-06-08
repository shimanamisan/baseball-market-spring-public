package com.shimanamisan.baseballmarket.message.application;

import com.shimanamisan.baseballmarket.message.domain.Board;
import com.shimanamisan.baseballmarket.message.domain.Message;
import java.util.List;

/**
 * 掲示板とそのメッセージ一覧をまとめて presentation 層へ受け渡すための DTO。
 */
public record BoardMessages(Board board, List<Message> messages) {}
