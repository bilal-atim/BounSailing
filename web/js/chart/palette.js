// Chart colours for the three themes, kept identical to the Android client's
// ChartPalette.kt so the same chart reads the same way on both.
//
// Day follows the paper-chart convention: white deep water, blue shallows, buff
// land. Night keeps the hierarchy but drops luminance hard and shifts accents to
// red/amber so the helm keeps its dark adaptation.

export const PALETTES = {
  day: {
    background: '#8FC4DE', deepWater: '#EAF6FC',
    depth200: '#DCEFF9', depth50: '#CDE7F6', depth20: '#B8DCF1',
    depth10: '#9BCDEB', depth5: '#79BBE3', depth2: '#54A6DA',
    unsafeWater: '#FF3B1F', cautionWater: '#FFB300',
    land: '#E8DCB5', landOutline: '#9A8C61', inlandWater: '#B8DCF1',
    contour: '#5E8FA8', contourMajor: '#3B6E88',
    contourLabel: '#2E5A72', contourLabelHalo: '#FFFFFF',
    sounding: '#1F4E63', soundingHalo: '#FFFFFF',
    structure: '#7A6E4E', road: '#C9A227', roadMinor: '#C9B98A',
    placeLabel: '#3A3221', placeHalo: '#FFFFFF', hazard: '#C62828',
    restrictedFill: '#D32F2F', restrictedLine: '#B71C1C',
    anchorageFill: '#1B7F5A', anchorageLine: '#0E5E41',
    route: '#7B1FA2', routeCasing: '#FFFFFF', routeActiveLeg: '#E91E63',
    track: '#00796B', waypoint: '#7B1FA2', waypointHalo: '#FFFFFF',
    bearingLine: '#E91E63', anchorCircle: '#0E5E41', anchorCircleAlarm: '#D32F2F',
    boat: '#D32F2F',
    ui: { surface: '#FFFFFF', onSurface: '#15202B', muted: '#41505C', accent: '#1B6785' },
  },
  dusk: {
    background: '#2C4A5C', deepWater: '#1B3040',
    depth200: '#1E3648', depth50: '#223D52', depth20: '#26455C',
    depth10: '#2B4E68', depth5: '#325A78', depth2: '#3A6889',
    unsafeWater: '#C0331B', cautionWater: '#B07A12',
    land: '#3B3A2C', landOutline: '#6B6647', inlandWater: '#26455C',
    contour: '#5A7E93', contourMajor: '#7FA6BC',
    contourLabel: '#9DBBCC', contourLabelHalo: '#101C24',
    sounding: '#BBD3E0', soundingHalo: '#101C24',
    structure: '#6A6247', road: '#8A7534', roadMinor: '#5C543A',
    placeLabel: '#D6D2C0', placeHalo: '#101C24', hazard: '#EF5350',
    restrictedFill: '#B23A3A', restrictedLine: '#E57373',
    anchorageFill: '#2E7D64', anchorageLine: '#66BB9A',
    route: '#CE93D8', routeCasing: '#101C24', routeActiveLeg: '#FF7BA8',
    track: '#4DB6AC', waypoint: '#CE93D8', waypointHalo: '#101C24',
    bearingLine: '#FF7BA8', anchorCircle: '#66BB9A', anchorCircleAlarm: '#EF5350',
    boat: '#FF5252',
    ui: { surface: '#16242E', onSurface: '#DCE5EB', muted: '#A7BAC6', accent: '#62A8CE' },
  },
  night: {
    background: '#0A0F14', deepWater: '#05080B',
    depth200: '#070B10', depth50: '#0A1016', depth20: '#0D151D',
    depth10: '#101B25', depth5: '#14222E', depth2: '#1A2C3B',
    unsafeWater: '#8C2413', cautionWater: '#6E4A08',
    land: '#171512', landOutline: '#4A4433', inlandWater: '#0D151D',
    contour: '#37505F', contourMajor: '#4E6F82',
    contourLabel: '#6C8A9C', contourLabelHalo: '#000000',
    sounding: '#8AA6B6', soundingHalo: '#000000',
    structure: '#3F3A2A', road: '#5A4B20', roadMinor: '#332E20',
    placeLabel: '#938E7A', placeHalo: '#000000', hazard: '#E53935',
    restrictedFill: '#6E2020', restrictedLine: '#B04A4A',
    anchorageFill: '#1B4D3E', anchorageLine: '#3E8C72',
    route: '#9C6BB0', routeCasing: '#000000', routeActiveLeg: '#E0407A',
    track: '#2E8B82', waypoint: '#9C6BB0', waypointHalo: '#000000',
    bearingLine: '#E0407A', anchorCircle: '#3E8C72', anchorCircleAlarm: '#E53935',
    boat: '#FF3B30',
    ui: { surface: '#0B0E11', onSurface: '#9A8A78', muted: '#7A6E60', accent: '#CC5533' },
  },
};

export const THEMES = ['day', 'dusk', 'night'];
export const THEME_LABELS = { day: 'Gündüz', dusk: 'Alacakaranlık', night: 'Gece' };
