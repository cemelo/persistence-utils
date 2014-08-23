/*
 * Copyright (C) 2014  Secretaria de Orçamento Federal
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package br.gov.siop.persistence.mongo

import com.allanbank.mongodb.Credential
import com.allanbank.mongodb.MongoClient
import com.allanbank.mongodb.MongoClientConfiguration
import com.allanbank.mongodb.MongoFactory
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

@Singleton(lazy = true)
class MongoClientProvider {

    private volatile MongoClient client

    static MongoClient getClient(Config config) {
        if (getInstance().client == null)
            getInstance().init(config)

        getInstance().client
    }

    static MongoClient getClient() {
        getClient(ConfigFactory.load())
    }

    private synchronized void init(Config config) {
        if (client != null)
            return

        MongoClientConfiguration clientConfiguration = new MongoClientConfiguration()

        config.getStringList('mongodb.servers').each { clientConfiguration.addServer it }

        // Cria as credenciais de acesso ao MongoDB
        config.getConfigList('mongodb.credentials').each {
            Credential.Builder c = Credential.builder()

            config.entrySet().each {
                switch (it.key) {
                    case 'username':
                        c.userName = it.value.render()
                        break;
                    case 'password':
                        c.password = it.value.render().toCharArray()
                        break;
                    case 'certificate':
                        new File(it.value.render()).withInputStream { is ->
                            c.x509(CertificateFactory.getInstance("X.509").generateCertificate(is) as X509Certificate)
                        }
                        break;
                    case 'database':
                        c.database = it.value.render()
                        break;
                    case 'file':
                        c.file = new File(it.value.render())
                        break;
                    case 'options':
                        (it.value as Config).entrySet().each {
                            switch (it.value.unwrapped().class) {
                                case Number:
                                    c.addOption it.key, (it.value.unwrapped() as Number).toInteger()
                                    break;
                                case Boolean:
                                    c.addOption it.key, it.value.unwrapped() as Boolean
                                    break;
                                default:
                                    c.addOption it.key, it.value.render()
                            }
                        }
                        break;
                }
            }

            if (it.hasPath('type'))
                c.invokeMethod it.getString('type'), []

            clientConfiguration.addCredential c
        }

        config.getConfig('mongodb.options').entrySet().each {
            if (clientConfiguration.hasProperty(it.key)) {
                if (clientConfiguration[it.key].class.isAssignableFrom(Integer)) {
                    clientConfiguration[it.key] = (it.value.unwrapped() as Number).intValue()
                } else if (clientConfiguration[it.key].class.isAssignableFrom(Long)) {
                    clientConfiguration[it.key] = (it.value.unwrapped() as Number).longValue()
                } else if (clientConfiguration[it.key].class.isAssignableFrom(Boolean)) {
                    clientConfiguration[it.key] = it.value.unwrapped() as Boolean
                } else if (clientConfiguration[it.key].class.isAssignableFrom(CharSequence)) {
                    clientConfiguration[it.key] = it.value.render()
                }
            }
        }

        client = MongoFactory.createClient(clientConfiguration)
    }
}
