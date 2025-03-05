package com.bbook.service;

import com.bbook.constant.OrderStatus;
import com.bbook.dto.OrderDto;
import com.bbook.dto.OrderHistDto;
import com.bbook.entity.Book;
import com.bbook.entity.Member;
import com.bbook.entity.Order;
import com.bbook.entity.OrderBook;
import com.bbook.repository.BookRepository;
import com.bbook.repository.MemberRepository;
import com.bbook.repository.OrderRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;


@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class OrderService {
	// 필요한 Repository들을 주입받음
	private final BookRepository bookRepository; // 상품 정보 관리
	private final MemberRepository memberRepository; // 회원 정보 관리
	private final OrderRepository orderRepository; // 주문 정보 관리

	// 아임포트 API 인증 정보
	@Value("${iamport.key}")
	private String iamportKey;

	@Value("${iamport.secret}")
	private String iamportSecret;

	// 단일 상품 주문 처리
	@Transactional
	public Long order(OrderDto orderDto, String email) {

		try {
			Book item = bookRepository.findById(orderDto.getBookId())
					.orElseThrow(EntityNotFoundException::new);

			Member member = memberRepository.findByEmail(email)
					.orElseThrow(() -> new EntityNotFoundException("회원을 찾을 수 없습니다."));

			OrderBook orderBook = OrderBook.createOrderBook(item, orderDto.getCount());

			Order order = Order.createOrder(member, List.of(orderBook), orderDto.getImpUid(),
					orderDto.getMerchantUid());

			orderRepository.save(order);

			return order.getId();
		} catch (Exception e) {
			log.error("주문 생성 중 오류 발생: {}", e.getMessage(), e);
			throw e;
		}
	}

	// 주문 목록 조회
	@Transactional(readOnly = true)
	public Page<OrderHistDto> getOrderList(String email, Pageable pageable) {
		List<Order> orders = orderRepository.findOrders(email, pageable);
		Long totalCount = orderRepository.countOrder(email);

		List<OrderHistDto> orderHistDtos = new ArrayList<>();

		for (Order order : orders) {
			OrderHistDto orderHistDto = new OrderHistDto(order);

			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
			orderHistDto.setOrderDate(order.getOrderDate().format(formatter));

			orderHistDtos.add(orderHistDto);
		}

		return new PageImpl<>(orderHistDtos, pageable, totalCount);
	}

	// 장바구니에서 여러 상품 주문
	@Transactional
	public Long orders(List<OrderDto> orderDtoList, String email) {
		Member member = memberRepository.findByEmail(email).orElseThrow(EntityNotFoundException::new);
		List<OrderBook> orderBooks = new ArrayList<>();

		for (OrderDto orderDto : orderDtoList) {
			Book item = bookRepository.findById(orderDto.getBookId())
					.orElseThrow(EntityNotFoundException::new);
			OrderBook orderBook = OrderBook.createOrderBook(item, orderDto.getCount());
			orderBooks.add(orderBook);
		}

		Order order = Order.createOrder(member, orderBooks, orderDtoList.get(0).getImpUid(),
				orderDtoList.get(0).getMerchantUid());
		if (orderDtoList.get(0).getImpUid() != null) {
			order.setImpUid(orderDtoList.get(0).getImpUid());
			order.setMerchantUid(orderDtoList.get(0).getMerchantUid());
		}
		orderRepository.save(order);

		return order.getId();
	}

	// 결제 정보 검증
	@Transactional(readOnly = true)
	public boolean verifyPayment(String impUid, String merchantUid, Long amount) {

			try {
				String[] parts = merchantUid.split("-");
				if (parts.length < 2) {
					return false;
				}

				if (amount == null || amount <= 0) {
					return false;
				}

				return true;

		} catch (Exception e) {
			log.error("Payment verification failed: " + e.getMessage(), e);
			return false;
		}
	}

	@Transactional
	public void cancelOrder(Long orderId) throws EntityNotFoundException {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new EntityNotFoundException("주문을 찾을 수 없습니다."));

		Member member = order.getMember();
		int usedPoints = order.getUsedPoints(); 
		int earnedPoints = order.getEarnedPoints();

		if (member.getPoint() < earnedPoints) {
			int shortagePoints = (int) (earnedPoints - member.getPoint());
			usedPoints = Math.max(0, usedPoints - shortagePoints);

			member.setPoint(0);
			member.addPoint(usedPoints);
		} else {
			member.addPoint(-earnedPoints);
			member.addPoint(usedPoints); 
		}
		order.cancelOrder();

		for (OrderBook orderBook : order.getOrderBooks()) {
			Book book = orderBook.getBook();
			book.addStock(orderBook.getCount());
		}
	}

	// 주문 ID로 주문 조회
	@Transactional(readOnly = true)
	public Order findById(Long orderId) {
		return orderRepository.findById(orderId)
				.orElseThrow(() -> new EntityNotFoundException("주문을 찾을 수 없습니다. ID: " + orderId));
	}

	@Transactional
	public Order saveOrder(Order order) {
		return orderRepository.save(order);
	}

	public boolean hasUserPurchasedBook(Long memberId, Long bookId) {
		return orderRepository
				.existsByMemberIdAndBookIdAndStatus(memberId, bookId, OrderStatus.PAID);
	}

	// 비슷한 취향의 회원들이 구매한 책 추천
	@Transactional(readOnly = true)
	public List<Book> getCollaborativeRecommendations(Long memberId) {
		List<Long> recentBookIds = orderRepository.findByMemberIdOrderByOrderDateDesc(memberId)
				.stream()
				.flatMap(order -> order.getOrderBooks().stream())
				.map(orderBook -> orderBook.getBook().getId())
				.distinct()
				.limit(5)
				.collect(Collectors.toList());

		if (recentBookIds.isEmpty()) {
			return bookRepository.findTop10ByOrderBySalesDesc();
		}
		return orderRepository.findCollaborativeBooks(recentBookIds, memberId, 10);
	}
}
