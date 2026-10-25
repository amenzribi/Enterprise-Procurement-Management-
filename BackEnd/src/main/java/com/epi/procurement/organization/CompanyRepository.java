package com.epi.procurement.organization;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

	List<Company> findByGroupIdOrderByCode(Long groupId);

}
