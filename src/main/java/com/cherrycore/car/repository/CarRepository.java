package com.cherrycore.car.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cherrycore.car.entity.Car;

public interface CarRepository extends JpaRepository<Car, Long> {
}