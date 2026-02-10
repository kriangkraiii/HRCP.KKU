package com.ecom.service;

import java.util.List;

import com.ecom.model.Notification;
import com.ecom.model.UserDtls;

public interface NotificationService {

	List<Notification> getUserNotifications(UserDtls user);

	long getUnreadCount(UserDtls user);

	void markAsRead(Long notificationId);

	void markAllAsRead(UserDtls user);
}