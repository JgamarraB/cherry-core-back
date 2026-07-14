package com.cherrycore.car.mapper;

import org.springframework.stereotype.Component;

import com.cherrycore.car.dto.CarResponse;
import com.cherrycore.car.dto.CreateCarRequest;
import com.cherrycore.car.dto.UpdateCarRequest;
import com.cherrycore.car.entity.Car;

@Component
public class CarMapper {

    public Car toEntity(CreateCarRequest request) {
        return new Car(
                request.title(),
                request.description(),
                request.price()
        );
    }

    public void updateEntity(Car car, UpdateCarRequest request) {
        car.setTitle(request.title());
        car.setDescription(request.description());
        car.setPrice(request.price());
    }

    public CarResponse toResponse(Car car) {
        return new CarResponse(
                car.getId(),
                car.getTitle(),
                car.getDescription(),
                car.getPrice(),
                car.getCreatedAt(),
                car.getUpdatedAt()
        );
    }
}