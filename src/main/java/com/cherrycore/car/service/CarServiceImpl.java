package com.cherrycore.car.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.car.dto.CarResponse;
import com.cherrycore.car.dto.CreateCarRequest;
import com.cherrycore.car.dto.UpdateCarRequest;
import com.cherrycore.car.entity.Car;
import com.cherrycore.car.exception.CarNotFoundException;
import com.cherrycore.car.mapper.CarMapper;
import com.cherrycore.car.repository.CarRepository;

@Service
@Transactional
public class CarServiceImpl implements CarService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CarServiceImpl.class);

    private static final String CAR_CREATED_LOG_MESSAGE =
            "Car created successfully with id: {}";

    private static final String CAR_UPDATED_LOG_MESSAGE =
            "Car updated successfully with id: {}";

    private static final String CAR_DELETED_LOG_MESSAGE =
            "Car deleted successfully with id: {}";

    /*
     * Las dependencias se declaran como final porque son obligatorias
     * para que este servicio funcione y no deben cambiar después
     * de crear la instancia.
     */
    private final CarRepository carRepository;
    private final CarMapper carMapper;

    /*
     * Spring crea automáticamente el repositorio y el mapper
     * e inyecta ambas dependencias mediante este constructor.
     *
     * Aunque solo exista un constructor y @Autowired sea opcional,
     * lo dejamos explícito para que el patrón sea fácil de reconocer.
     */
    @Autowired
    public CarServiceImpl(
            CarRepository carRepository,
            CarMapper carMapper
    ) {
        this.carRepository = carRepository;
        this.carMapper = carMapper;
    }

    /*
     * Crea un nuevo coche.
     *
     * 1. Convierte el DTO recibido en una entidad Car.
     * 2. Guarda la entidad en la base de datos.
     * 3. Registra en el log el identificador generado.
     * 4. Convierte la entidad guardada en un DTO de respuesta.
     */
    @Override
    public CarResponse createCar(CreateCarRequest request) {
        Car car = carMapper.toEntity(request);

        Car savedCar = carRepository.save(car);

        LOGGER.info(CAR_CREATED_LOG_MESSAGE, savedCar.getId());

        return carMapper.toResponse(savedCar);
    }

    /*
     * Obtiene todos los coches.
     *
     * readOnly = true indica que esta operación solo consulta datos
     * y no debería modificar registros de la base de datos.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CarResponse> findAllCars() {
        return carRepository.findAll()
                .stream()
                .map(carMapper::toResponse)
                .toList();

        /*
         * La versión anterior usando un bucle for sería:
         *
         * List<Car> cars = carRepository.findAll();
         * List<CarResponse> responses = new ArrayList<>();
         *
         * for (Car car : cars) {
         *     CarResponse response = carMapper.toResponse(car);
         *     responses.add(response);
         * }
         *
         * return responses;
         *
         * El stream realiza el mismo proceso:
         *
         * stream()
         *     Recorre la colección de coches.
         *
         * map(carMapper::toResponse)
         *     Convierte cada entidad Car en un CarResponse.
         *
         * toList()
         *     Agrupa todos los resultados en una nueva lista.
         */
    }

    /*
     * Busca un coche por su identificador.
     *
     * El método privado findCarEntityById centraliza la búsqueda
     * y lanza CarNotFoundException cuando el coche no existe.
     */
    @Override
    @Transactional(readOnly = true)
    public CarResponse findCarById(Long carId) {
        Car car = findCarEntityById(carId);

        return carMapper.toResponse(car);
    }

    /*
     * Actualiza completamente un coche existente.
     *
     * 1. Busca el coche en la base de datos.
     * 2. Copia sobre la entidad los datos recibidos en el DTO.
     * 3. Guarda los cambios.
     * 4. Devuelve el coche actualizado.
     */
    @Override
    public CarResponse updateCar(
            Long carId,
            UpdateCarRequest request
    ) {
        Car car = findCarEntityById(carId);

        carMapper.updateEntity(car, request);

        Car updatedCar = carRepository.save(car);

        LOGGER.info(CAR_UPDATED_LOG_MESSAGE, updatedCar.getId());

        return carMapper.toResponse(updatedCar);
    }

    /*
     * Elimina un coche.
     *
     * Primero buscamos la entidad para comprobar que realmente existe.
     * Si no existe, se lanzará CarNotFoundException.
     */
    @Override
    public void deleteCar(Long carId) {
        Car car = findCarEntityById(carId);

        carRepository.delete(car);

        LOGGER.info(CAR_DELETED_LOG_MESSAGE, carId);
    }

    /*
     * Método auxiliar privado para evitar repetir la misma búsqueda
     * en findCarById, updateCar y deleteCar.
     *
     * findById devuelve un Optional<Car>.
     * Si contiene una entidad, se devuelve.
     * Si está vacío, orElseThrow lanza la excepción personalizada.
     */
    private Car findCarEntityById(Long carId) {
        return carRepository.findById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));
    }
}