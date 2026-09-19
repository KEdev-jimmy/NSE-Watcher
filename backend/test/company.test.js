const assert = require('node:assert/strict');
const test = require('node:test');

const { repairAnnualGrowth } = require('../api/company');

test('repairAnnualGrowth preserves provider-supplied growth values', () => {
  const result = {
    profile: {
      revenueGrowth: '30.17%',
      profitGrowth: '133.91%',
      epsGrowth: '133.91%',
    },
    financialHistory: [
      {
        period: 'FY 2024',
        revenue: '815.23',
        profit: '116.27',
        eps: '0.45',
        revenueGrowth: '',
        profitGrowth: '',
        epsGrowth: '',
      },
      {
        period: 'FY 2025',
        revenue: '1,061',
        profit: '272.24',
        eps: '1.04',
        // Deliberately different from calculations based on rounded values.
        revenueGrowth: '30.17%',
        profitGrowth: '133.91%',
        epsGrowth: '133.91%',
      },
    ],
  };

  repairAnnualGrowth(result);

  assert.equal(result.financialHistory[1].revenueGrowth, '30.17%');
  assert.equal(result.financialHistory[1].profitGrowth, '133.91%');
  assert.equal(result.financialHistory[1].epsGrowth, '133.91%');
  assert.equal(result.profile.revenueGrowth, '30.17%');
  assert.equal(result.profile.profitGrowth, '133.91%');
  assert.equal(result.profile.epsGrowth, '133.91%');
});

test('repairAnnualGrowth calculates growth only when provider growth is missing', () => {
  const result = {
    profile: {},
    financialHistory: [
      {
        period: 'FY 2024',
        revenue: '100',
        profit: '20',
        eps: '1.00',
        revenueGrowth: '',
        profitGrowth: '',
        epsGrowth: '',
      },
      {
        period: 'FY 2025',
        revenue: '125',
        profit: '30',
        eps: '1.25',
        revenueGrowth: '',
        profitGrowth: '',
        epsGrowth: '',
      },
    ],
  };

  repairAnnualGrowth(result);

  assert.equal(result.financialHistory[1].revenueGrowth, '25.00%');
  assert.equal(result.financialHistory[1].profitGrowth, '50.00%');
  assert.equal(result.financialHistory[1].epsGrowth, '25.00%');
  assert.equal(result.profile.revenueGrowth, '25.00%');
  assert.equal(result.profile.profitGrowth, '50.00%');
  assert.equal(result.profile.epsGrowth, '25.00%');
});

test('repairAnnualGrowth can use the provider profile growth when the latest row is missing it', () => {
  const result = {
    profile: {
      revenueGrowth: '30.17%',
      profitGrowth: '133.91%',
      epsGrowth: '133.91%',
    },
    financialHistory: [
      {
        period: 'FY 2024',
        revenue: '815.23',
        profit: '116.27',
        eps: '0.45',
      },
      {
        period: 'FY 2025',
        revenue: '1,061',
        profit: '272.24',
        eps: '1.04',
      },
    ],
  };

  repairAnnualGrowth(result);

  assert.equal(result.profile.revenueGrowth, '30.17%');
  assert.equal(result.profile.profitGrowth, '133.91%');
  assert.equal(result.profile.epsGrowth, '133.91%');
});
