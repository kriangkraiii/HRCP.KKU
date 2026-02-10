package com.ecom.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.model.Notification;
import com.ecom.model.UserDtls;
import com.ecom.repository.NotificationRepository;
import com.ecom.service.NotificationService;
import com.ecom.util.CommonUtil;

@Service
public class NotificationServiceImpl implements NotificationService {

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private CommonUtil commonUtil;

	@Override
	public List<Notification> getUserNotifications(UserDtls user) {
		return notificationRepository.findByRecipientOrderByCreatedAtDesc(user);
	}

	@Override
	public long getUnreadCount(UserDtls user) {
		return notificationRepository.countUnreadByRecipient(user);
	}

	@Override
	@Transactional
	public void markAsRead(Long notificationId) {
		notificationRepository.markAsRead(notificationId);
	}

	@Override
	@Transactional
	public void markAllAsRead(UserDtls user) {
		notificationRepository.markAllAsReadByRecipient(user);
	}
}