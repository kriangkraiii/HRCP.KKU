package com.ecom.config;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.UserService;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

	private final UserRepository userRepository;

	private final UserService userService;

	public UserDetailsServiceImpl(UserRepository userRepository, UserService userService) {
		this.userRepository = userRepository;
		this.userService = userService;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

		UserDtls user = userRepository.findByEmail(username);

		if (user == null) {
			throw new UsernameNotFoundException("user not found");
		}

		// Auto-unlock if lock time has expired (persists across server restarts)
		// BUT do NOT unlock deactivated accounts (isEnable=false) — those were banned
		if (Boolean.FALSE.equals(user.getAccountNonLocked())
				&& user.getLockTime() != null
				&& Boolean.TRUE.equals(user.getIsEnable())) {
			userService.unlockAccountTimeExpired(user);
			// Re-fetch to get the updated state
			user = userRepository.findByEmail(username);
		}

		return new CustomUser(user);
	}

}
