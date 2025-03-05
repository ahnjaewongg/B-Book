package com.bbook.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.bbook.repository.BookRepository;

import lombok.RequiredArgsConstructor;

import com.bbook.entity.Book;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MainBookService {

  private final BookRepository bookRepository;
  private static final int LIST_SIZE = 15;

  public List<Book> getBooksByCategory(String main, String mid, String detail) {
    if (detail != null) {
      return bookRepository.findByMainCategoryAndMidCategoryAndDetailCategory(main, mid, detail);
    } else if (mid != null) {
      return bookRepository.findByMainCategoryAndMidCategory(main, mid);
    } else if (main != null) {
      return bookRepository.findByMainCategory(main);
    }
    return bookRepository.findAll();
  }

  public List<Book> getRecommendedBooks() {
    return bookRepository.findTop10ByOrderByIdAsc(PageRequest.of(0, 10));
  }

  public List<Book> getBestBooks() {
    return bookRepository.findTop15ByOrderByViewCountDesc(
        PageRequest.of(0, LIST_SIZE));
  }

  public List<Book> getNewBooks() {
    return bookRepository.findTop15ByOrderByCreatedAtDesc(
        PageRequest.of(0, LIST_SIZE));
  }
}