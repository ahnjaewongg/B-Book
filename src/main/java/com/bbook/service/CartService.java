package com.bbook.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.util.StringUtils;

import com.bbook.dto.CartDetailDto;
import com.bbook.dto.CartBookDto;
import com.bbook.dto.CartOrderDto;
import com.bbook.dto.OrderDto;
import com.bbook.dto.OrderBookDto;
import com.bbook.entity.Book;
import com.bbook.entity.Cart;
import com.bbook.entity.CartBook;
import com.bbook.entity.Member;
import com.bbook.entity.Order;
import com.bbook.entity.OrderBook;
import com.bbook.repository.CartBookRepository;
import com.bbook.repository.CartRepository;
import com.bbook.repository.BookRepository;
import com.bbook.repository.MemberRepository;
import com.bbook.repository.OrderRepository;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
@Transactional
public class CartService {

	private static final Logger log = LoggerFactory.getLogger(CartService.class);

	private final BookRepository bookRepository;
	private final MemberRepository memberRepository;
	private final CartRepository cartRepository;
	private final CartBookRepository cartBookRepository;
	private final OrderRepository orderRepository;

	public Long addCart(CartBookDto cartBookDto, String email) {
		Book book = bookRepository.findById(cartBookDto.getBookId())
				.orElseThrow(EntityNotFoundException::new);

		Member member = memberRepository.findByEmail(email).orElseThrow(EntityNotFoundException::new);
		Cart cart = cartRepository.findByMemberId(member.getId());

		if (cart == null) {
			cart = Cart.createCart(member);
			cartRepository.save(cart);
		}

		CartBook savedCartBook = cartBookRepository.findByCartIdAndBookId(cart.getId(),
				book.getId());

		if (savedCartBook != null) {
			savedCartBook.addCount(cartBookDto.getCount()); // 있는 객체에 수량 증가
			return savedCartBook.getId();
		} else {
			CartBook cartBook = CartBook.createCartBook(cart, book,
					cartBookDto.getCount());
			cartBookRepository.save(cartBook);
			return cartBook.getId();
		}
	}


	// 장바구니 목록 조회
	@Transactional(readOnly = true)
	public List<CartDetailDto> getCartList(String email) {
		List<CartDetailDto> cartDetailDtoList = new ArrayList<>();

		Member member = memberRepository.findByEmail(email).orElseThrow(EntityNotFoundException::new);

		Cart cart = cartRepository.findByMemberId(member.getId());

		if (cart == null) {
			return cartDetailDtoList;
		}

		cartDetailDtoList = cartBookRepository.findCartDetailDtoList(cart.getId());

		return cartDetailDtoList;
	}

	@Transactional(readOnly = true)
	public boolean validateCartBook(Long cartBookId, String email) {

		Member curMember = memberRepository.findByEmail(email).orElseThrow(EntityNotFoundException::new);

		CartBook cartBook = cartBookRepository.findById(cartBookId)
				.orElseThrow(EntityExistsException::new);

		Member savedMember = cartBook.getCart().getMember();

		if (!StringUtils.equals(curMember.getEmail(), savedMember.getEmail())) {
			return false;
		}
		return true;
	}

	// 장바구니 상품 수량 업데이트
	@Transactional
	public void updateCartBookCount(Long cartBookId, int count) {
		CartBook cartBook = cartBookRepository.findById(cartBookId)
				.orElseThrow(() -> new EntityNotFoundException("장바구니 상품을 찾을 수 없습니다."));
		cartBook.updateCount(count);
	}

	// 장바구니 상품 삭제
	@Transactional
	public Long deleteCartBook(Long cartBookId) {
		CartBook cartBook = cartBookRepository.findById(cartBookId)
				.orElseThrow(EntityExistsException::new);
		Long bookId = cartBook.getBook().getId();
		cartBookRepository.delete(cartBook);
		return bookId;
	}

	@Transactional
	public void deleteCartBooks(List<CartOrderDto> cartOrderDtoList, String email) {
		try {
			log.info("장바구니 삭제 시작 - 이메일: {}, 상품 개수: {}", email, cartOrderDtoList.size());

			Member member = memberRepository.findByEmail(email)
					.orElseThrow(() -> new EntityNotFoundException("회원을 찾을 수 없습니다."));
			Cart cart = cartRepository.findByMemberId(member.getId());

			if (cart == null) {
				return;
			}

			List<Long> cartBookIds = cartOrderDtoList.stream()
					.map(CartOrderDto::getCartBookId)
					.collect(Collectors.toList());

			List<CartBook> cartBooks = cartBookRepository.findAllById(cartBookIds);

			for (CartBook cartBook : cartBooks) {
				cart.getCartBooks().remove(cartBook);
				cartBook.setCart(null);
			}
			cartRepository.saveAndFlush(cart);

			for (CartBook cartBook : cartBooks) {
				cartBookRepository.delete(cartBook);
			}
			cartBookRepository.flush();

		} catch (Exception e) {
			throw e;
		}
	}

	@Transactional
	public Long orderCartBook(List<CartOrderDto> cartOrderDtoList, String email,
			String impUid, String merchantUid, int usedPoints, int discountAmount) {
		long totalAmount = 0;
		List<OrderBook> orderBooks = new ArrayList<>();
		Map<Long, CartBook> cartBooks = new HashMap<>();

		for (CartOrderDto cartOrderDto : cartOrderDtoList) {
			CartBook cartBook = cartBookRepository.findById(cartOrderDto.getCartBookId())
					.orElseThrow(() -> new EntityNotFoundException("장바구니 상품을 찾을 수 없습니다."));
			cartBooks.put(cartBook.getId(), cartBook);

			long bookPrice = cartBook.getBook().getPrice() * cartBook.getCount();
			totalAmount += bookPrice;
		}

		for (CartBook cartBook : cartBooks.values()) {
			OrderBook orderBook = OrderBook.createOrderBook(cartBook.getBook(), cartBook.getCount());
			orderBooks.add(orderBook);
		}

		Member member = memberRepository.findByEmail(email)
				.orElseThrow(() -> new EntityNotFoundException("회원을 찾을 수 없습니다."));

		Order order = Order.createOrder(member, orderBooks, impUid, merchantUid);
		order.setOriginalPrice(totalAmount);

		order.setUsedPoints(usedPoints);
		order.setDiscountAmount(discountAmount);
		order.setIsCouponUsed(discountAmount > 0);

		long finalPrice = totalAmount - usedPoints - discountAmount;
		order.setTotalPrice(finalPrice);

		orderRepository.save(order);
		orderRepository.flush();

		return order.getId();
	}

	@Transactional(readOnly = true)
	public OrderDto createTempOrderInfo(List<CartOrderDto> cartOrderDtoList, String email) {
		Member member = memberRepository.findByEmail(email).orElseThrow(EntityNotFoundException::new);
		List<CartBook> cartBooks = new ArrayList<>();

		for (CartOrderDto cartOrderDto : cartOrderDtoList) {
			CartBook cartBook = cartBookRepository.findById(cartOrderDto.getCartBookId())
					.orElseThrow(EntityNotFoundException::new);
			cartBooks.add(cartBook);
		}

		// 주문 정보 생성
		OrderDto orderDto = new OrderDto();
		Long originalPrice = calculateTotalPrice(cartBooks); 
		Long totalPrice = originalPrice; 

		// 주문 정보 설정
		orderDto.setOrderName(createOrderName(cartBooks));
		orderDto.setOriginalPrice(originalPrice); 
		orderDto.setTotalPrice(totalPrice); 
		orderDto.setCount(calculateTotalCount(cartBooks));
		orderDto.setEmail(email);
		orderDto.setName(member.getName());
		orderDto.setPhone(member.getPhone());
		orderDto.setAddress(member.getAddress());
		orderDto.setIsCouponUsed(false); 
		orderDto.setUsedPoints(0); 
		orderDto.setDiscountAmount(0);

		// 주문 상품 목록 설정
		for (CartBook cartBook : cartBooks) {
			Book book = cartBook.getBook();
			OrderBookDto orderBookDto = new OrderBookDto(
					OrderBook.createOrderBook(book, cartBook.getCount()),
					book.getImageUrl());
			orderDto.getOrderBookDtoList().add(orderBookDto);
		}

		// 첫 번째 상품의 대표 이미지 URL 설정
		if (!cartBooks.isEmpty()) {
			Book book = cartBooks.get(0).getBook();
			orderDto.setImageUrl(book.getImageUrl());
			orderDto.setBookId(book.getId());
		}

		return orderDto;
	}

	private String createOrderName(List<CartBook> cartBooks) {
		String firstBookName = cartBooks.get(0).getBook().getTitle();
		if (cartBooks.size() > 1) {
			return firstBookName + " 외 " + (cartBooks.size() - 1) + "건";
		}
		return firstBookName;
	}

	private Long calculateTotalPrice(List<CartBook> cartBooks) {
		return cartBooks.stream()
				.mapToLong(cartBook -> (long) cartBook.getBook().getPrice() * cartBook.getCount())
				.sum();
	}

	private int calculateTotalCount(List<CartBook> cartBooks) {
		return cartBooks.stream()
				.mapToInt(CartBook::getCount)
				.sum();
	}

}