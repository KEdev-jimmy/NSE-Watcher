const handleMovement = require('../lib/movementIntelligence');

// Keep this route as a thin Vercel function wrapper so /api/moving
// always resolves to the movement-intelligence backend implementation.
module.exports = handleMovement;
