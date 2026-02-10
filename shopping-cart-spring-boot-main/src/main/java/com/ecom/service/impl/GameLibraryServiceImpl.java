package com.ecom.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ecom.model.GameLibrary;
import com.ecom.model.Product;
import com.ecom.model.UserDtls;
import com.ecom.repository.GameLibraryRepository;
import com.ecom.service.GameLibraryService;

@Service
public class GameLibraryServiceImpl implements GameLibraryService {

	@Autowired
	private GameLibraryRepository gameLibraryRepository;

	@Override
	public GameLibrary addToLibrary(UserDtls user, Product product, String orderId, String gameKey) {
		// Check if user already owns this game
		if (gameLibraryRepository.existsByUserIdAndProductId(user.getId(), product.getId())) {
			return null; // Already owned
		}

		GameLibrary gameLibrary = new GameLibrary();
		gameLibrary.setUser(user);
		gameLibrary.setProduct(product);
		gameLibrary.setOrderId(orderId);
		gameLibrary.setGameKey(gameKey);
		gameLibrary.setPurchaseDate(LocalDateTime.now());
		gameLibrary.setIsDownloaded(false);

		return gameLibraryRepository.save(gameLibrary);
	}

	@Override
	public List<GameLibrary> getGamesByUser(Integer userId) {
		return gameLibraryRepository.findByUserIdOrderByPurchaseDateDesc(userId);
	}

	@Override
	public Boolean isGameOwned(Integer userId, Integer productId) {
		return gameLibraryRepository.existsByUserIdAndProductId(userId, productId);
	}

	@Override
	public GameLibrary markAsDownloaded(Integer gameLibraryId) {
		Optional<GameLibrary> optionalGame = gameLibraryRepository.findById(gameLibraryId);
		if (optionalGame.isPresent()) {
			GameLibrary game = optionalGame.get();
			game.setIsDownloaded(true);
			return gameLibraryRepository.save(game);
		}
		return null;
	}

	@Override
	public GameLibrary getGameLibraryById(Integer id) {
		return gameLibraryRepository.findById(id).orElse(null);
	}
}
