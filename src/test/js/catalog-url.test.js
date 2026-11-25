/*
 * Проверка клиентской логики каталога: сборка и разбор URL-параметров (ТЗ п. 4.7.9).
 * Запуск: node src/test/js/catalog-url.test.js
 */
const assert = require('node:assert');

/** Копия функции FindSport.catalogUrl из src/main/resources/static/js/api.js. */
function catalogUrl(state) {
    const query = new URLSearchParams();
    Object.entries(state).forEach(([key, value]) => {
        if (value === '' || value === null || value === undefined || value === false) {
            return;
        }
        if (key === 'page' && !value) {
            return;
        }
        if (Array.isArray(value)) {
            value.forEach(item => query.append(key, item));
            return;
        }
        query.set(key, value);
    });
    const suffix = query.toString();
    return '/catalog.html' + (suffix ? '?' + suffix : '');
}

// Пустое состояние — ссылка без параметров
assert.strictEqual(catalogUrl({}), '/catalog.html');

// Только заполненные значения попадают в URL
assert.strictEqual(
    catalogUrl({q: 'забег', city: '', regionId: null, sportId: '', hasFreeSeats: false, page: 0}),
    '/catalog.html?q=%D0%B7%D0%B0%D0%B1%D0%B5%D0%B3');

// Включенный признак передается как true, выключенный не передается вовсе
assert.strictEqual(catalogUrl({hasFreeSeats: true}), '/catalog.html?hasFreeSeats=true');
assert.strictEqual(catalogUrl({hasFreeSeats: false}), '/catalog.html');

// Несколько значений одного параметра повторяются
assert.strictEqual(
    catalogUrl({type: ['EVENT', 'ORGANIZATION']}),
    '/catalog.html?type=EVENT&type=ORGANIZATION');

// Ненулевая страница сохраняется
assert.strictEqual(catalogUrl({page: 3}), '/catalog.html?page=3');

// Параметры читаются обратно из ссылки
const link = catalogUrl({q: 'забег', city: 'Минск', sort: 'DATE', page: 2});
const parsed = new URL('http://localhost' + link).searchParams;
assert.strictEqual(parsed.get('q'), 'забег');
assert.strictEqual(parsed.get('city'), 'Минск');
assert.strictEqual(parsed.get('sort'), 'DATE');
assert.strictEqual(parsed.get('page'), '2');

// Ссылка на результат открывает каталог с теми же параметрами
const back = new URLSearchParams({q: 'забег', city: 'Минск'}).toString();
assert.strictEqual(new URL('http://localhost/event.html?id=5&back=' + encodeURIComponent(back)).searchParams.get('back'),
    'q=%D0%B7%D0%B0%D0%B1%D0%B5%D0%B3&city=%D0%9C%D0%B8%D0%BD%D1%81%D0%BA');

console.log('Проверки URL-параметров каталога пройдены.');
