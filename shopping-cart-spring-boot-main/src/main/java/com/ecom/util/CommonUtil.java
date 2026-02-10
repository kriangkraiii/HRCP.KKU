package com.ecom.util;

import java.io.UnsupportedEncodingException;
import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.model.ProductOrder;
import com.ecom.model.UserDtls;
import com.ecom.service.FileService;
import com.ecom.service.UserService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class CommonUtil {

	@Autowired
	private JavaMailSender mailSender;
	
	@Autowired
	private UserService userService;
	
	@Value("${aws.s3.bucket.category}")
	private String categoryBucket;

	@Value("${aws.s3.bucket.product}")
	private String productBucket;

	@Value("${aws.s3.bucket.profile}")
	private String profileBucket;

	@Value("${aws.s3.bucket.petprofile}")
	private String petprofileBucket;

	@Value("${aws.s3.bucket.petpost}")
	private String petpostBucket;

	public Boolean sendMail(String url, String reciepentEmail) throws UnsupportedEncodingException, MessagingException {

	    MimeMessage message = mailSender.createMimeMessage();
	    MimeMessageHelper helper = new MimeMessageHelper(message);

	    helper.setFrom("daspabitra55@gmail.com", "Shopping Cart");
	    helper.setTo(reciepentEmail);

	    String content = "<p>Hello,</p>" 
	            + "<p>You have requested to reset your password.</p>"
	            + "<p>Click the link below to change your password:</p>" 
	            + "<p><a href=\"" + url + "\">Change my password</a></p>";
//	            + "<br>"
//	            + "<p>If the above link doesn't work, copy and paste this URL into your browser:</p>"
//	            + "<p>" + url + "</p>";
	    
	    helper.setSubject("Password Reset");
	    helper.setText(content, true); // true enables HTML content
	    mailSender.send(message);
	    return true;
	}
	
	// เพิ่ม method ใหม่สำหรับส่ง email แจ้งเตือน
			public Boolean sendNotificationEmail(String recipientEmail, String subject, String content) 
			        throws UnsupportedEncodingException, MessagingException {
			    
			    MimeMessage message = mailSender.createMimeMessage();
			    MimeMessageHelper helper = new MimeMessageHelper(message);

			    helper.setFrom("seven1aaplus@gmail.com", "Pet Community");
			    helper.setTo(recipientEmail);
			    helper.setSubject(subject);
			    helper.setText(content, true);
			    
			    mailSender.send(message);
			    return true;
			}


	public static String generateUrl(HttpServletRequest request) {

		// http://localhost:8080/forgot-password
		String siteUrl = request.getRequestURL().toString();

		return siteUrl.replace(request.getServletPath(), "");
	}
	
	String msg=null;;
	
	public Boolean sendMailForProductOrder(ProductOrder order,String status) throws Exception
	{
		
		msg="<p>Hello [[name]],</p>"
				+ "<p>Thank you order <b>[[orderStatus]]</b>.</p>"
				+ "<p><b>Product Details:</b></p>"
				+ "<p>Name : [[productName]]</p>"
				+ "<p>Category : [[category]]</p>"
				+ "<p>Quantity : [[quantity]]</p>"
				+ "<p>Price : [[price]]</p>"
				+ "<p>Payment Type : [[paymentType]]</p>";
		
		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message);

		helper.setFrom("seven1aaplus@gmail.com", "Shooping Cart");
		helper.setTo(order.getOrderAddress().getEmail());

		msg=msg.replace("[[name]]",order.getOrderAddress().getFirstName());
		msg=msg.replace("[[orderStatus]]",status);
		msg=msg.replace("[[productName]]", order.getProduct().getTitle());
		msg=msg.replace("[[category]]", order.getProduct().getCategory());
		msg=msg.replace("[[quantity]]", order.getQuantity().toString());
		msg=msg.replace("[[price]]", order.getPrice().toString());
		msg=msg.replace("[[paymentType]]", order.getPaymentType());
		
		helper.setSubject("Product Order Status");
		helper.setText(msg, true);
		mailSender.send(message);
		return true;
	}
	
	public UserDtls getLoggedInUserDetails(Principal p) {
		String email = p.getName();
		UserDtls userDtls = userService.getUserByEmail(email);
		return userDtls;
	}
	
	public String getImageUrl(MultipartFile file, Integer bucketType) {
		String bucketName = null;
		
		if (bucketType == 1) {
			bucketName = categoryBucket;
		} else if (bucketType == 2) {

			bucketName = productBucket;
		} else if (bucketType == 3) {
			bucketName = profileBucket;
		} else if (bucketType == 4) {
			bucketName = petprofileBucket;
		} else if (bucketType == 5) {
			bucketName = petpostBucket;
		}else {
			throw new IllegalArgumentException("Invalid bucket type");
		}
		
		String imageName = file != null ? file.getOriginalFilename() : "default.png";
		
		String url = "https://"+bucketName+".s3.amazonaws.com/"+imageName;
		return url;
	
	}
	

}
