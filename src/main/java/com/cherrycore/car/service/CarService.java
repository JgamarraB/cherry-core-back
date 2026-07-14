package com.cherrycore.car.service;

import java.util.List;

import com.cherrycore.car.dto.CarResponse;
import com.cherrycore.car.dto.CreateCarRequest;
import com.cherrycore.car.dto.UpdateCarRequest;

public interface CarService {

    CarResponse createCar(CreateCarRequest request);

    List<CarResponse> findAllCars();

    CarResponse findCarById(Long carId);

    CarResponse updateCar(Long carId, UpdateCarRequest request);

    void deleteCar(Long carId);
}