(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var success = style.getPropertyValue('--success').trim();
  var error = style.getPropertyValue('--error').trim();

  // --- Chart: 修复前后计价误差对比 ---
  var chartSummary = echarts.init(document.getElementById('chart-summary'), null, { renderer: 'svg' });
  chartSummary.setOption({
    animation: false,
    tooltip: {
      trigger: 'axis',
      appendToBody: true,
      axisPointer: { type: 'shadow' }
    },
    legend: {
      data: ['修复前计价误差数', '修复后计价误差数'],
      textStyle: { color: ink, fontSize: 13 },
      top: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '18%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: ['九州', '呼兰中医', '电机厂', '祖研南岗'],
      axisLabel: { color: ink, fontSize: 13 },
      axisLine: { lineStyle: { color: rule } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      name: '计价误差条数',
      nameTextStyle: { color: muted, fontSize: 12 },
      axisLabel: { color: muted, fontSize: 12 },
      axisLine: { show: false },
      splitLine: { lineStyle: { color: rule, type: 'dashed' } }
    },
    series: [
      {
        name: '修复前计价误差数',
        type: 'bar',
        data: [2, 1, 1, 10],
        itemStyle: { color: error, borderRadius: [4, 4, 0, 0] },
        barWidth: '30%',
        label: {
          show: true,
          position: 'top',
          color: error,
          fontWeight: 600,
          fontSize: 13
        }
      },
      {
        name: '修复后计价误差数',
        type: 'bar',
        data: [0, 0, 0, 0],
        itemStyle: { color: success, borderRadius: [4, 4, 0, 0] },
        barWidth: '30%',
        label: {
          show: true,
          position: 'top',
          color: success,
          fontWeight: 600,
          fontSize: 13,
          formatter: '✓ 0'
        }
      }
    ]
  });
  window.addEventListener('resize', function() { chartSummary.resize(); });
})();
