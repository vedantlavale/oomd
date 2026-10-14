package com.vsms.service;

import com.vsms.dto.SparePartForm;
import com.vsms.entity.SparePart;
import com.vsms.exception.BusinessRuleException;
import com.vsms.exception.ResourceNotFoundException;
import com.vsms.repository.PartUsageRepository;
import com.vsms.repository.SparePartRepository;
import com.vsms.util.MoneyUtil;
import com.vsms.util.Paging;
import com.vsms.util.Texts;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SparePartService {

    private final SparePartRepository parts;
    private final PartUsageRepository usages;

    public SparePartService(SparePartRepository parts, PartUsageRepository usages) {
        this.parts = parts;
        this.usages = usages;
    }

    @Transactional(readOnly = true)
    public Page<SparePart> search(String query, boolean lowStockOnly, int page) {
        return parts.search(Texts.like(query), lowStockOnly, Paging.of(page, Sort.by("name", "id")));
    }

    @Transactional(readOnly = true)
    public List<SparePart> findAll() {
        return parts.findAllByOrderByName();
    }

    @Transactional(readOnly = true)
    public long countLowStock() {
        return parts.countLowStock();
    }

    @Transactional(readOnly = true)
    public SparePart get(Long id) {
        return parts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Spare part", id));
    }

    @Transactional(readOnly = true)
    public boolean isUsed(Long id) {
        return usages.existsBySparePartId(id);
    }

    public SparePart create(SparePartForm form) {
        SparePart part = new SparePart();
        apply(part, form, false);
        return saveChecked(part);
    }

    /** Changing the price never changes what was already charged on jobs (they keep their own copy). */
    public SparePart update(Long id, SparePartForm form) {
        SparePart part = parts.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Spare part", id));
        apply(part, form, true);
        return saveChecked(part);
    }

    /** Adds delivered stock to the shelf. */
    public SparePart addStock(Long id, int quantity) {
        if (quantity < 1 || quantity > 100000) {
            throw new BusinessRuleException("Enter a quantity between 1 and 100000.");
        }
        SparePart part = parts.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Spare part", id));
        part.setStockQuantity(part.getStockQuantity() + quantity);
        return part;
    }

    public void delete(Long id) {
        SparePart part = get(id);
        if (isUsed(id)) {
            throw new BusinessRuleException("\"" + part.getName() + "\" has been used on jobs and cannot be deleted. "
                    + "Set its stock to 0 instead.");
        }
        parts.delete(part);
    }

    private void apply(SparePart part, SparePartForm form, boolean existing) {
        String sku = form.getSku().trim().toUpperCase(Locale.ROOT);
        Optional<SparePart> duplicate = parts.findBySku(sku);
        if (duplicate.isPresent() && (!existing || !duplicate.get().getId().equals(part.getId()))) {
            throw new BusinessRuleException("sku", "SKU " + sku + " is already used by \"" + duplicate.get().getName() + "\".");
        }
        part.setSku(sku);
        part.setName(Texts.clean(form.getName()));
        part.setUnitPrice(MoneyUtil.money(form.getUnitPrice()));
        part.setStockQuantity(form.getStockQuantity());
        part.setReorderLevel(form.getReorderLevel());
    }

    private SparePart saveChecked(SparePart part) {
        try {
            return parts.saveAndFlush(part);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("sku", "SKU " + part.getSku() + " is already in use.");
        }
    }
}
