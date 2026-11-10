package tn.esprit.studentmanagement.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.studentmanagement.entities.Department;
import tn.esprit.studentmanagement.repositories.DepartmentRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentService departmentService;

    private static Department department(Long id, String name) {
        Department department = new Department();
        department.setIdDepartment(id);
        department.setName(name);
        return department;
    }

    @Test
    @DisplayName("getAllDepartments returns every department the repository holds")
    void returnsEveryDepartment() {
        Department informatics = department(1L, "Informatics");
        when(departmentRepository.findAll()).thenReturn(List.of(informatics));

        assertThat(departmentService.getAllDepartments()).containsExactly(informatics);
    }

    @Test
    @DisplayName("getDepartmentById returns the department when it exists")
    void returnsDepartmentById() {
        Department informatics = department(2L, "Informatics");
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(informatics));

        assertThat(departmentService.getDepartmentById(2L)).isSameAs(informatics);
    }

    @Test
    @DisplayName("getDepartmentById returns null for an unknown id")
    void returnsNullForUnknownId() {
        when(departmentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThat(departmentService.getDepartmentById(404L)).isNull();
    }

    @Test
    @DisplayName("saveDepartment stores the entity and returns the saved copy")
    void savesDepartment() {
        Department informatics = department(null, "Informatics");
        when(departmentRepository.save(informatics)).thenReturn(informatics);

        assertThat(departmentService.saveDepartment(informatics)).isSameAs(informatics);
        verify(departmentRepository).save(informatics);
    }

    @Test
    @DisplayName("deleteDepartment deletes by id")
    void deletesDepartment() {
        departmentService.deleteDepartment(8L);

        verify(departmentRepository).deleteById(8L);
    }
}
