// SOS Clinic - infraestructura minima para una demo academica en Azure.
//
// Un solo grupo de recursos contiene:
//   * VM Ubuntu 24.04 con IP publica estatica y DNS <label>.<region>.cloudapp.azure.com
//   * Docker + Caddy instalados por cloud-init
//   * ACR Basic para las 7 imagenes
//   * Storage Account privada para backups
//   * Identidad administrada de la VM con AcrPull y Blob Data Contributor
//   * Auto-shutdown y alerta de presupuesto
//
// No crea bases gestionadas: PostgreSQL y MySQL corren como contenedores en la VM.

targetScope = 'resourceGroup'

@description('Region de Azure. deploy.ps1 valida disponibilidad antes de desplegar.')
param location string = resourceGroup().location

@description('Prefijo de los recursos que no requieren nombre global.')
@minLength(3)
@maxLength(24)
param namePrefix string = 'sos-clinic'

@description('Etiqueta DNS publica. Debe ser unica en la region.')
@minLength(3)
@maxLength(63)
param dnsLabel string

@description('Nombre global del Azure Container Registry (solo alfanumerico).')
@minLength(5)
@maxLength(50)
param acrName string

@description('Nombre global de la Storage Account (minusculas y numeros, 3-24).')
@minLength(3)
@maxLength(24)
param storageName string

@description('Contenedor de blobs para los backups.')
@minLength(3)
@maxLength(63)
param backupContainerName string = 'sos-clinic-backups'

@description('Tamano de VM. B2ms, D2as_v5 y D2as_v7 tienen 2 vCPU y 8 GB.')
@allowed([
  'Standard_B2ms'
  'Standard_B4ms'
  'Standard_D2as_v5'
  'Standard_D2s_v5'
  'Standard_D2as_v7'
  'Standard_D2s_v7'
])
param vmSize string = 'Standard_B2ms'

@description('Usuario Linux administrador. cloud-init asume azureuser.')
@minLength(1)
@maxLength(32)
param adminUsername string = 'azureuser'

@description('Llave publica SSH (contenido de id_ed25519.pub).')
@secure()
param sshPublicKey string

@description('CIDR autorizado para SSH, por ejemplo 190.1.2.3/32.')
param adminSourcePrefix string

@description('Correo para la alerta de presupuesto. Vacio desactiva la alerta.')
param alertEmail string = ''

@description('Monto mensual de la alerta de presupuesto en USD.')
param budgetAmount int = 60

@description('Primer dia del mes para el periodo del presupuesto.')
param budgetStartDate string = utcNow('yyyy-MM-01')

@description('Hora local de auto-shutdown en formato HHmm.')
param autoShutdownTime string = '2200'

@description('Zona horaria Windows del auto-shutdown. SA Pacific = Bogota, UTC-5.')
param autoShutdownTimeZone string = 'SA Pacific Standard Time'

@description('Fecha estimada de fin del trial, solo para etiquetas y documentacion.')
param trialEndDate string = ''

@description('Tamano del disco del sistema en GB.')
param osDiskSizeGB int = 64

var commonTags = {
  project: 'sos-clinic'
  environment: 'demo'
  managedBy: 'bicep'
  trialEnds: empty(trialEndDate) ? 'no-definida' : trialEndDate
}

var vnetName = '${namePrefix}-vnet'
var nsgName = '${namePrefix}-nsg'
var pipName = '${namePrefix}-pip'
var nicName = '${namePrefix}-nic'
var vmName = '${namePrefix}-vm'
var subnetName = 'sos-subnet'

// ---------------------------------------------------------------------------
// Red
// ---------------------------------------------------------------------------

resource nsg 'Microsoft.Network/networkSecurityGroups@2024-05-01' = {
  name: nsgName
  location: location
  tags: commonTags
  properties: {
    securityRules: [
      {
        name: 'AllowSSH'
        properties: {
          priority: 1000
          direction: 'Inbound'
          access: 'Allow'
          protocol: 'Tcp'
          sourcePortRange: '*'
          destinationPortRange: '22'
          sourceAddressPrefix: adminSourcePrefix
          destinationAddressPrefix: '*'
        }
      }
      {
        name: 'AllowHTTP'
        properties: {
          priority: 1010
          direction: 'Inbound'
          access: 'Allow'
          protocol: 'Tcp'
          sourcePortRange: '*'
          destinationPortRange: '80'
          sourceAddressPrefix: '*'
          destinationAddressPrefix: '*'
        }
      }
      {
        name: 'AllowHTTPS'
        properties: {
          priority: 1020
          direction: 'Inbound'
          access: 'Allow'
          protocol: 'Tcp'
          sourcePortRange: '*'
          destinationPortRange: '443'
          sourceAddressPrefix: '*'
          destinationAddressPrefix: '*'
        }
      }
      {
        name: 'DenyAllInbound'
        properties: {
          priority: 4096
          direction: 'Inbound'
          access: 'Deny'
          protocol: '*'
          sourcePortRange: '*'
          destinationPortRange: '*'
          sourceAddressPrefix: '*'
          destinationAddressPrefix: '*'
        }
      }
    ]
  }
}

resource vnet 'Microsoft.Network/virtualNetworks@2024-05-01' = {
  name: vnetName
  location: location
  tags: commonTags
  properties: {
    addressSpace: {
      addressPrefixes: [
        '10.42.0.0/16'
      ]
    }
    subnets: [
      {
        name: subnetName
        properties: {
          addressPrefix: '10.42.1.0/24'
          networkSecurityGroup: {
            id: nsg.id
          }
        }
      }
    ]
  }
}

resource pip 'Microsoft.Network/publicIPAddresses@2024-05-01' = {
  name: pipName
  location: location
  tags: commonTags
  sku: {
    name: 'Standard'
    tier: 'Regional'
  }
  properties: {
    publicIPAllocationMethod: 'Static'
    dnsSettings: {
      domainNameLabel: dnsLabel
    }
    idleTimeoutInMinutes: 4
  }
}

resource nic 'Microsoft.Network/networkInterfaces@2024-05-01' = {
  name: nicName
  location: location
  tags: commonTags
  properties: {
    ipConfigurations: [
      {
        name: 'ipconfig1'
        properties: {
          privateIPAllocationMethod: 'Dynamic'
          subnet: {
            id: vnet.properties.subnets[0].id
          }
          publicIPAddress: {
            id: pip.id
          }
        }
      }
    ]
  }
}

// ---------------------------------------------------------------------------
// Identidad y VM
// ---------------------------------------------------------------------------

resource vm 'Microsoft.Compute/virtualMachines@2024-07-01' = {
  name: vmName
  location: location
  tags: commonTags
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    hardwareProfile: {
      vmSize: vmSize
    }
    osProfile: {
      computerName: vmName
      adminUsername: adminUsername
      customData: loadFileAsBase64('cloud-init.yaml')
      linuxConfiguration: {
        disablePasswordAuthentication: true
        ssh: {
          publicKeys: [
            {
              path: '/home/${adminUsername}/.ssh/authorized_keys'
              keyData: sshPublicKey
            }
          ]
        }
      }
    }
    storageProfile: {
      imageReference: {
        publisher: 'Canonical'
        offer: 'ubuntu-24_04-lts'
        sku: 'server'
        version: 'latest'
      }
      osDisk: {
        createOption: 'FromImage'
        diskSizeGB: osDiskSizeGB
        deleteOption: 'Delete'
        managedDisk: {
          storageAccountType: 'StandardSSD_LRS'
        }
      }
    }
    networkProfile: {
      networkInterfaces: [
        {
          id: nic.id
          properties: {
            deleteOption: 'Delete'
          }
        }
      ]
    }
    diagnosticsProfile: {
      bootDiagnostics: {
        enabled: false
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Azure Container Registry
// ---------------------------------------------------------------------------

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  tags: commonTags
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: false
    publicNetworkAccess: 'Enabled'
    networkRuleBypassOptions: 'AzureServices'
  }
}

// Roles necesarios para que la VM descargue imagenes y suba backups sin claves.
var acrPullRoleId = '7f951dda-4ed3-4680-a7ca-43fe172d538d'
var blobDataContributorRoleId = 'ba92f5b4-2d11-453d-a403-e96b0029c9fe'

resource acrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(acr.id, vm.id, acrPullRoleId)
  scope: acr
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', acrPullRoleId)
    principalId: vm.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

// ---------------------------------------------------------------------------
// Storage para backups
// ---------------------------------------------------------------------------

resource storage 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: storageName
  location: location
  tags: commonTags
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'StorageV2'
  properties: {
    accessTier: 'Hot'
    allowBlobPublicAccess: false
    allowSharedKeyAccess: false
    minimumTlsVersion: 'TLS1_2'
    supportsHttpsTrafficOnly: true
    publicNetworkAccess: 'Enabled'
    networkAcls: {
      defaultAction: 'Allow'
      bypass: 'AzureServices'
    }
  }
}

resource blobService 'Microsoft.Storage/storageAccounts/blobServices@2023-05-01' = {
  parent: storage
  name: 'default'
  properties: {
    deleteRetentionPolicy: {
      enabled: true
      days: 7
    }
  }
}

resource backupContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-05-01' = {
  parent: blobService
  name: backupContainerName
  properties: {
    publicAccess: 'None'
  }
}

resource blobDataContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(storage.id, vm.id, blobDataContributorRoleId)
  scope: storage
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', blobDataContributorRoleId)
    principalId: vm.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

// Los backups se borran despues de 30 dias; el disco de la VM conserva 7 dias.
resource lifecycle 'Microsoft.Storage/storageAccounts/managementPolicies@2023-05-01' = {
  parent: storage
  name: 'default'
  properties: {
    policy: {
      rules: [
        {
          enabled: true
          name: 'expirar-backups'
          type: 'Lifecycle'
          definition: {
            filters: {
              blobTypes: [
                'blockBlob'
              ]
              prefixMatch: [
                'sos-clinic/'
              ]
            }
            actions: {
              baseBlob: {
                delete: {
                  daysAfterModificationGreaterThan: 30
                }
              }
            }
          }
        }
      ]
    }
  }
}

// ---------------------------------------------------------------------------
// Auto-shutdown y presupuesto
// ---------------------------------------------------------------------------

resource autoShutdown 'Microsoft.DevTestLab/schedules@2018-09-15' = {
  name: 'shutdown-computevm-${vm.name}'
  location: location
  tags: commonTags
  properties: {
    status: 'Enabled'
    taskType: 'ComputeVmShutdownTask'
    dailyRecurrence: {
      time: autoShutdownTime
    }
    timeZoneId: autoShutdownTimeZone
    targetResourceId: vm.id
    notificationSettings: {
      status: 'Disabled'
      timeInMinutes: 30
    }
  }
  dependsOn: [
    vm
  ]
}

resource budget 'Microsoft.Consumption/budgets@2023-11-01' = if (!empty(alertEmail)) {
  name: '${namePrefix}-presupuesto'
  properties: {
    category: 'Cost'
    amount: budgetAmount
    timeGrain: 'Monthly'
    timePeriod: {
      startDate: budgetStartDate
    }
    notifications: {
      actual50: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 50
        thresholdType: 'Actual'
        contactEmails: [
          alertEmail
        ]
      }
      actual80: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 80
        thresholdType: 'Actual'
        contactEmails: [
          alertEmail
        ]
      }
      forecast100: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 100
        thresholdType: 'Forecasted'
        contactEmails: [
          alertEmail
        ]
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Salidas
// ---------------------------------------------------------------------------

output resourceGroup string = resourceGroup().name
output location string = location
output vmName string = vm.name
output vmSize string = vmSize
output fqdn string = pip.properties.dnsSettings.fqdn
output publicIp string = pip.properties.ipAddress
output sshCommand string = 'ssh ${adminUsername}@${pip.properties.dnsSettings.fqdn}'
output acrName string = acr.name
output acrLoginServer string = acr.properties.loginServer
output storageAccount string = storage.name
output backupContainer string = backupContainerName
output nsgName string = nsg.name
output adminUsername string = adminUsername
output autoShutdownTime string = autoShutdownTime
output autoShutdownTimeZone string = autoShutdownTimeZone
