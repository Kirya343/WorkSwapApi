document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.listing-small-card-stat-body-item').forEach(item => {
        item.addEventListener('mouseenter', () => {
            const type = item.id;
            const id = item.getAttribute('listingId');
            const p = document.getElementById('monthly-' + type + '-' + id);
            if (p) {
                p.style.display = 'block';
                setTimeout(() => {
                    p.classList.add('active');
                }, 10); //  
            }
        });
        item.addEventListener('mouseleave', () => {
            const type = item.id;
            const id = item.getAttribute('listingId');
            const p = document.getElementById('monthly-' + type + '-' + id);
            if (p) {
                p.style.display = 'none';
                setTimeout(() => {
                    p.classList.add('active');
                }, 10); // 
            }
        });
    });
});

let chartInstance = null;
let listingId = null;
let isVisible = false;

function destroyChart() {
    if (chartInstance) {
        chartInstance.destroy();
        chartInstance = null;
    }
}

function controlStats(btn) {
    const listingId = btn.getAttribute('listingId');
    const statPanel = document.getElementById('statPanelCard' + listingId);
    const statPanels = document.querySelectorAll('.stat-panel-card');
    if (chartInstance) {
        chartInstance.destroy();
    }

    statPanels.forEach(panel => {
        panel.style.display = 'none';
    });

    if (isVisible) {
        console.log("панель уже активирована активирована");
        statPanel.style.display = 'none';
        isVisible = false;
        return;
    } else {
        console.log("Панель " + listingId + " ещё не активирована");
        statPanel.style.display = 'block';
        isVisible = true;
    }

    const intervalSelect = statPanel.querySelector('.interval');
    const daysSelect = statPanel.querySelector('.days-select');
    const canvas = statPanel.querySelector('#statsChart' + listingId);

    intervalSelect.addEventListener('change', () => {
        loadStatistic();
        destroyChart();
    });
    daysSelect.addEventListener('change', () => {
        loadStatistic();
        destroyChart();
    });

    function loadStatistic() {
        const interval = intervalSelect.value;
        const days = daysSelect.value;

        fetch(`/api/stats/views?listingId=${listingId}&interval=${interval}&days=${days}`)
        .then(r => r.json())
        .then(data => {
            const labels = data.map(p => p.x);
            const values = data.map(p => p.y);

            const ctx = canvas.getContext('2d');

            const min = Math.min(...values);
            const max = Math.max(...values);
            const range = max - min;

            chartInstance = new Chart(ctx, {
                type: 'line',
                data: {
                    labels,
                    datasets: [{
                                label: 'Просмотры',
                                data: values,
                                borderColor: 'rgba(99, 102, 241, 1)',
                                backgroundColor: 'rgba(99, 102, 241, 0.2)',
                                fill: true,
                                tension: 0,

                                pointRadius: 6, 
                                pointBackgroundColor: 'rgba(75, 192, 192, 0)', 
                                pointBorderColor: 'rgba(75, 192, 192, 0)',    
                                pointHoverRadius: 10,

                                borderWidth: 4
                            }]
                },
                options: {
                    responsive: true,
                    scales: {
                        x: { display: false },
                        y: {
                            suggestedMin: min - range * 0.25,
                            suggestedMax: max + range * 0.25,
                            ticks: {
                                callback: v => Number.isInteger(v) && v > 0 ? v : null
                            }
                        }
                    }
                }
            });
        })
        .catch(console.error);
    }

    loadStatistic();
}