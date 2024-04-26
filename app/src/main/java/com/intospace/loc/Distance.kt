package com.intospace.loc

class Distance(var dlat: Double, var dlon: Double, var dalt: Double, var dh: Double) {
    companion object {
        const val EARTH_RADIUS_METERS = 6371000.0
        private fun getHaversineDistance(
            lat1Degree: Double,
            lng1Degree: Double,
            lat2Degree: Double,
            lng2Degree: Double
        ): Double {
            val R = 6371000.0 // Earth's radius in kilometers
            val lat1_rad = Math.toRadians(lat1Degree)
            val lon1_rad = Math.toRadians(lng1Degree)
            val lat2_rad = Math.toRadians(lat2Degree)
            val lon2_rad = Math.toRadians(lng2Degree)
            val dlat = lat2_rad - lat1_rad
            val dlon = lon2_rad - lon1_rad
            val a = Math.pow(
                Math.sin(dlat / 2),
                2.0
            ) + Math.cos(lat1_rad) * Math.cos(lat2_rad) * Math.pow(
                Math.sin(dlon / 2), 2.0
            )
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            var distance = R * c
            if (lng1Degree == lng2Degree && dlat < 0) distance *= -1.0 else if (lat1Degree == lat2Degree && dlon < 0) distance *= -1.0
            return distance // Convert to meters
        }

        /**
         * Return the distance (measured along the surface of the sphere) between 2 points
         */
        fun getDistanceMeters(
            lat1Degree: Double, lng1Degree: Double, alt1Meter: Double,
            lat2Degree: Double, lng2Degree: Double, alt2Meter: Double
        ): Distance {
            val latDistance = getHaversineDistance(lat1Degree, lng1Degree, lat2Degree, lng1Degree)
            val lonDistance = getHaversineDistance(lat1Degree, lng1Degree, lat1Degree, lng2Degree)
            val hDistance = getHaversineDistance(lat1Degree, lng1Degree, lat2Degree, lng2Degree)
            return Distance(latDistance, lonDistance, alt2Meter - alt1Meter, hDistance)
        }
    }
}