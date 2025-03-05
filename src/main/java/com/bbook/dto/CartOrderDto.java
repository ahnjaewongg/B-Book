package com.bbook.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CartOrderDto {
	private Long cartBookId;
	private Integer count;

	private List<CartOrderDto> cartOrderDtoList;
}
