package com.ecom.config;

import java.util.Arrays;
import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.ecom.model.UserDtls;

public class CustomUser implements UserDetails {

	private static final long serialVersionUID = 1L;
	private UserDtls user;

	public CustomUser(UserDtls user) {
		super();
		this.user = user;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		String role = user != null ? user.getRole() : "ROLE_USER";
		SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role);
		return Arrays.asList(authority);
	}

	@Override
	public String getPassword() {
		return user != null ? user.getPassword() : "";
	}

	@Override
	public String getUsername() {
		return user != null ? user.getEmail() : "";
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return user != null ? user.getAccountNonLocked() : false;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return user != null ? user.getIsEnable() : false;
	}

}
