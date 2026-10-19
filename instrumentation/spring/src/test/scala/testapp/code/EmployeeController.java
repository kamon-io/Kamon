package testapp.code;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
class EmployeeController {

    private final EmployeeRepository repository;

    EmployeeController(EmployeeRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/throwIO")
    List<Employee> throwIO() throws IOException {
        if (0 < 1) {
            throw new IOException();
        }
        return repository.findAll();
    }


    // Aggregate root
    // tag::get-aggregate-root[]
    @GetMapping("/employees")
    List<Employee> all() {
        return repository.findAll();
    }
    // end::get-aggregate-root[]

    @PostMapping("/employees")
    Employee newEmployee(@RequestBody Employee newEmployee) {
        Employee emp = repository.save(newEmployee);
        return emp;
    }

    // Single item

    @GetMapping("/employees/{id}/foo/{test}")
    Employee one(@PathVariable(value = "id") Long id,
                 @PathVariable(value = "test") String test) {
        Employee emp = repository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        return emp;
    }

    @PutMapping("/employees/{id}")
    Employee replaceEmployee(@RequestBody Employee newEmployee, @PathVariable(value = "id") Long id) {
        Employee emp = repository.findById(id)
                .map(employee -> {
                    employee.setName(newEmployee.getName());
                    employee.setRole(newEmployee.getRole());
                    return repository.save(employee);
                })
                .orElseGet(() -> {
                    newEmployee.setId(id);
                    return repository.save(newEmployee);
                });
        return emp;
    }

    @DeleteMapping("/employees/{id}")
    void deleteEmployee(@PathVariable(value = "id") Long id) {
        repository.deleteById(id);
    }
}
