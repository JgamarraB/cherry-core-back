package com.cherrycore.car.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.car.dto.CarResponse;
import com.cherrycore.car.dto.CreateCarRequest;
import com.cherrycore.car.dto.UpdateCarRequest;
import com.cherrycore.car.service.CarService;
import com.cherrycore.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping(CarController.CARS_PATH)
public class CarController {

    public static final String CARS_PATH = "/api/cars";

    private static final String CAR_ID_PATH = "/{carId}";

    private final CarService carService;

    /*
     * Spring inyecta la implementación de CarService mediante
     * el constructor.
     *
     * El controlador depende de la interfaz CarService y no de
     * la implementación concreta CarServiceImpl.
     */
    @Autowired
    public CarController(CarService carService) {
        this.carService = carService;
    }

    /*
     * Crea un coche nuevo.
     *
     * @RequestBody convierte el JSON recibido en CreateCarRequest.
     * @Valid ejecuta las validaciones declaradas en el DTO.
     *
     * Devuelve HTTP 201 Created y la respuesta estándar de la API.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CarResponse>> createCar(
            @Valid @RequestBody CreateCarRequest request
    ) {
        CarResponse createdCar = carService.createCar(request);

        ApiResponse<CarResponse> response =
                ApiResponse.success(createdCar);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /*
     * Obtiene todos los coches.
     *
     * Si no existen registros, el campo data contendrá una lista vacía.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CarResponse>>> findAllCars() {
        List<CarResponse> cars = carService.findAllCars();

        ApiResponse<List<CarResponse>> response =
                ApiResponse.success(cars);

        return ResponseEntity.ok(response);
    }

    /*
     * Obtiene un coche mediante su identificador.
     *
     * @PathVariable recoge el valor de carId desde la URL.
     *
     * Si el coche no existe, el servicio lanza
     * CarNotFoundException.
     */
    @GetMapping(CAR_ID_PATH)
    public ResponseEntity<ApiResponse<CarResponse>> findCarById(
            @PathVariable Long carId
    ) {
        CarResponse car = carService.findCarById(carId);

        ApiResponse<CarResponse> response =
                ApiResponse.success(car);

        return ResponseEntity.ok(response);
    }

    /*
     * Actualiza completamente un coche existente.
     *
     * Al tratarse de una petición PUT, deben enviarse todos los
     * campos obligatorios definidos en UpdateCarRequest.
     */
    @PutMapping(CAR_ID_PATH)
    public ResponseEntity<ApiResponse<CarResponse>> updateCar(
            @PathVariable Long carId,
            @Valid @RequestBody UpdateCarRequest request
    ) {
        CarResponse updatedCar =
                carService.updateCar(carId, request);

        ApiResponse<CarResponse> response =
                ApiResponse.success(updatedCar);

        return ResponseEntity.ok(response);
    }

    /*
     * Elimina un coche existente.
     *
     * Devuelve HTTP 200 en lugar de 204 porque queremos mantener
     * la estructura JSON estándar:
     *
     * {
     *   "success": true,
     *   "data": null
     * }
     */
    @DeleteMapping(CAR_ID_PATH)
    public ResponseEntity<ApiResponse<Void>> deleteCar(
            @PathVariable Long carId
    ) {
        carService.deleteCar(carId);

        ApiResponse<Void> response =
                ApiResponse.success(null);

        return ResponseEntity.ok(response);
    }
}