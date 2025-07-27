const toggles = document.querySelectorAll('.theme-toggle'); // используем класс
const savedTheme = localStorage.getItem('theme');

// Функция для применения темы
function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('theme', theme);
    // синхронизируем все переключатели
    toggles.forEach(toggle => {
        toggle.checked = theme === 'dark';
    });
}

// Инициализация
if (savedTheme) {
    applyTheme(savedTheme);
} else {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    applyTheme(prefersDark ? 'dark' : 'light');
}

// Обработчики событий
toggles.forEach(toggle => {
    toggle.addEventListener('change', function () {
        applyTheme(this.checked ? 'dark' : 'light');
    });
});