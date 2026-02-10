package com.ecom.service;

import java.util.List;

import com.ecom.model.GameLibrary;
import com.ecom.model.Product;
import com.ecom.model.UserDtls;

public interface GameLibraryService {

	GameLibrary addToLibrary(UserDtls user, Product product, String orderId, String gameKey);

	List<GameLibrary> getGamesByUser(Integer userId);

	Boolean isGameOwned(Integer userId, Integer productId);

	GameLibrary markAsDownloaded(Integer gameLibraryId);

	GameLibrary getGameLibraryById(Integer id);
}
