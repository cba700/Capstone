package com.capstone.domain.child.entity;

import com.capstone.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity

public class Child {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(nullable = false, length = 40)
	private String name;

	@Column(nullable = false)
	private Integer age;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Gender gender;

	public enum Gender{
		BOY,GIRL
	}
}
