/**
 * Searchable Select Component
 * Converts standard <select> elements into searchable dropdowns with live filtering.
 */
class SearchableSelect {
    constructor(selectElement, options = {}) {
        this.select = selectElement;
        this.options = Object.assign({
            placeholder: '— พิมพ์ค้นหาหรือเลือกจากรายการ —',
            searchPlaceholder: 'พิมพ์ชื่อ, อีเมล หรือตำแหน่งเพื่อค้นหา...'
        }, options);

        if (this.select.dataset.searchableInitialized === 'true') {
            return;
        }

        this.init();
    }

    init() {
        this.select.dataset.searchableInitialized = 'true';
        this.select.style.display = 'none';

        // Wrapper
        this.wrapper = document.createElement('div');
        this.wrapper.className = 'searchable-select-wrapper';
        this.select.parentNode.insertBefore(this.wrapper, this.select);
        this.wrapper.appendChild(this.select);

        // Toggle Button
        this.btn = document.createElement('div');
        this.btn.className = 'searchable-select-btn';
        this.btn.tabIndex = 0;
        this.btn.innerHTML = `
            <span class="selected-text"></span>
            <i class="fas fa-chevron-down text-muted small ms-2"></i>
        `;
        this.wrapper.appendChild(this.btn);

        // Dropdown Menu
        this.dropdown = document.createElement('div');
        this.dropdown.className = 'searchable-select-dropdown';
        this.dropdown.innerHTML = `
            <div class="searchable-select-search">
                <i class="fas fa-search search-icon"></i>
                <input type="text" placeholder="${this.options.searchPlaceholder}" autocomplete="off">
            </div>
            <ul class="searchable-select-options"></ul>
        `;
        this.wrapper.appendChild(this.dropdown);

        this.searchInput = this.dropdown.querySelector('input');
        this.optionsList = this.dropdown.querySelector('.searchable-select-options');

        this.buildOptions();
        this.updateButtonText();
        this.bindEvents();
    }

    buildOptions() {
        this.optionsList.innerHTML = '';
        const children = Array.from(this.select.children);

        if (children.length === 0) {
            this.showEmptyMessage('ไม่มีตัวเลือก');
            return;
        }

        children.forEach(child => {
            if (child.tagName === 'OPTGROUP') {
                const groupLabel = document.createElement('li');
                groupLabel.className = 'searchable-select-group-label';
                groupLabel.textContent = child.label;
                this.optionsList.appendChild(groupLabel);

                Array.from(child.children).forEach(opt => {
                    this.createOptionItem(opt, groupLabel);
                });
            } else if (child.tagName === 'OPTION') {
                this.createOptionItem(child, null);
            }
        });
    }

    createOptionItem(option, parentGroup) {
        const item = document.createElement('li');
        item.className = 'searchable-select-item';
        item.dataset.value = option.value;
        item.textContent = option.textContent.trim();

        if (option.disabled) {
            item.classList.add('is-disabled');
        }
        if (option.selected) {
            item.classList.add('is-selected');
        }

        item.addEventListener('click', (e) => {
            e.stopPropagation();
            if (option.disabled) return;
            this.selectValue(option.value);
            this.close();
        });

        this.optionsList.appendChild(item);
    }

    selectValue(val) {
        this.select.value = val;
        this.select.dispatchEvent(new Event('change', { bubbles: true }));
        this.updateButtonText();
        
        // Update selected class
        this.optionsList.querySelectorAll('.searchable-select-item').forEach(item => {
            if (item.dataset.value === val) {
                item.classList.add('is-selected');
            } else {
                item.classList.remove('is-selected');
            }
        });
    }

    updateButtonText() {
        const selectedOpt = this.select.options[this.select.selectedIndex];
        const textSpan = this.btn.querySelector('.selected-text');
        if (selectedOpt && selectedOpt.value !== '') {
            textSpan.textContent = selectedOpt.textContent.trim();
            textSpan.classList.remove('text-muted');
        } else {
            textSpan.textContent = selectedOpt ? selectedOpt.textContent.trim() : this.options.placeholder;
            textSpan.classList.add('text-muted');
        }
    }

    filterOptions(query) {
        const q = query.trim().toLowerCase();
        let visibleCount = 0;
        let currentGroupHeader = null;
        let groupHasVisibleItems = false;

        const items = Array.from(this.optionsList.children);

        items.forEach(el => {
            if (el.classList.contains('searchable-select-group-label')) {
                if (currentGroupHeader && !groupHasVisibleItems) {
                    currentGroupHeader.style.display = 'none';
                }
                currentGroupHeader = el;
                groupHasVisibleItems = false;
                el.style.display = '';
            } else if (el.classList.contains('searchable-select-item')) {
                const text = el.textContent.toLowerCase();
                if (text.includes(q)) {
                    el.style.display = '';
                    visibleCount++;
                    groupHasVisibleItems = true;
                } else {
                    el.style.display = 'none';
                }
            }
        });

        if (currentGroupHeader && !groupHasVisibleItems) {
            currentGroupHeader.style.display = 'none';
        }

        // Empty message handling
        let emptyEl = this.optionsList.querySelector('.searchable-select-empty');
        if (visibleCount === 0) {
            if (!emptyEl) {
                emptyEl = document.createElement('li');
                emptyEl.className = 'searchable-select-empty';
                this.optionsList.appendChild(emptyEl);
            }
            emptyEl.textContent = `ไม่พบข้อมูลที่ตรงกับ "${query}"`;
            emptyEl.style.display = '';
        } else if (emptyEl) {
            emptyEl.style.display = 'none';
        }
    }

    open() {
        // Close any other open dropdowns
        document.querySelectorAll('.searchable-select-dropdown.show').forEach(d => {
            if (d !== this.dropdown) d.classList.remove('show');
        });
        document.querySelectorAll('.searchable-select-btn.is-open').forEach(b => {
            if (b !== this.btn) b.classList.remove('is-open');
        });

        this.dropdown.classList.add('show');
        this.btn.classList.add('is-open');
        this.searchInput.value = '';
        this.filterOptions('');
        setTimeout(() => this.searchInput.focus(), 50);
    }

    close() {
        this.dropdown.classList.remove('show');
        this.btn.classList.remove('is-open');
    }

    toggle() {
        if (this.dropdown.classList.contains('show')) {
            this.close();
        } else {
            this.open();
        }
    }

    bindEvents() {
        this.btn.addEventListener('click', (e) => {
            e.stopPropagation();
            this.toggle();
        });

        this.btn.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
                e.preventDefault();
                this.open();
            }
        });

        this.searchInput.addEventListener('input', (e) => {
            this.filterOptions(e.target.value);
        });

        this.searchInput.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                this.close();
                this.btn.focus();
            }
        });

        document.addEventListener('click', (e) => {
            if (!this.wrapper.contains(e.target)) {
                this.close();
            }
        });
    }
}

// Auto-initialize on DOMContentLoaded
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('select.searchable-select, select.signer-searchable-select').forEach(el => {
        new SearchableSelect(el);
    });
});
