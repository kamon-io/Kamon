import Vue from 'vue'
import Vuetify from 'vuetify/lib/framework'
import {Iconfont} from 'vuetify/types/services/icons'

Vue.use(Vuetify)

export default new Vuetify({
    icons: {
        iconfont: 'fa' as Iconfont,
    },
    theme: {
        dark: false,
        themes: {
            light: {
                primary: '#3BC882',
                dark: '#1C1C28',
                background: '#E5E5E5',
                warning: '#FFCC00',
                error: '#FF3B3B',
                red4: '#FFE5E5',
                green4: '#E3FFF1',
                dark1: '#28293D',
                dark3: '#8F90A6',
                dark4: '#C7C9D9',
                light2: '#F2F2F5',
            },
        },
    },
})
