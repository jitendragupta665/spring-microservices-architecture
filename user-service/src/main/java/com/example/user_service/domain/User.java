package com.example.user_service.domain;

/**
 * A user of the shop. A Java record: an immutable data carrier. The compiler writes the
 * constructor, getters (id(), name(), age()), equals/hashCode and toString, and Jackson
 * turns it into JSON {"id":1,"name":"Rahul","age":22} without any annotations.
 */
public record User(long id, String name, int age) {
}
