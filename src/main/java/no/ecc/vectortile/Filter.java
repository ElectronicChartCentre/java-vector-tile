package no.ecc.vectortile;

abstract class Filter {

    public abstract boolean include(String layerName);

    public static final Filter ALL = new Filter() {

        @Override
        public boolean include(String layerName) {
            return true;
        }

    };

    public static final class Single extends Filter {

        private final String layerName;

        public Single(String layerName) {
            this.layerName = layerName;
        }

        @Override
        public boolean include(String layerName) {
            return this.layerName.equals(layerName);
        }

    }

}
