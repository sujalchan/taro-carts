package nz.ac.aut.comp713.taro_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import nz.ac.aut.comp713.taro_service.model.TaroType;

// provides database access and standard CRUD operations for TaroType entities
public interface TaroTypeRepository extends JpaRepository<TaroType, Long> {
}
