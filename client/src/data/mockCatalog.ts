import type { Catalog } from '../types/catalog'

export const mockCatalog: Catalog = {
  app: {
    name: 'Helianthus API',
  },
  datasources: {
    default: {
      type: 'postgres',
    },
  },
  queries: {
    'products.base': {
      datasource: 'default',
      sql: 'SELECT * FROM classicmodels.products',
    },
    'productlines.base': {
      datasource: 'default',
      sql: 'SELECT * FROM classicmodels.productlines',
    },
  },
  operations: {
    'all-products': {
      query: 'SELECT * FROM classicmodels.products',
      configurations: {
        default: {
          pipeline: [{ limit: 100 }],
        },
      },
    },
    'all-productlines': {
      query: 'SELECT * FROM classicmodels.productlines',
      configurations: {
        default: {
          pipeline: [{ limit: 100 }],
        },
      },
    },
    'get-product': {
      query: 'SELECT * FROM classicmodels.products WHERE productCode = ?',
      parameters: [
        {
          name: 'productCode',
          type: 'string',
          required: true,
        },
      ],
      configurations: {
        default: {
          pipeline: [{ limit: 100 }],
        },
      },
    },
    products: {
      queryRef: 'products.base',
      configurations: {
        default: {
          pipeline: [{ limit: 100 }],
        },
        compact: {
          pipeline: [
            { project: ['productCode', 'productName', 'productLine'] },
            { limit: 50 },
          ],
        },
        expensive: {
          pipeline: [
            { filter: { buyPrice: { gt: 50 } } },
            { project: ['productCode', 'productName', 'buyPrice'] },
            { limit: 100 },
          ],
        },
      },
    },
  },
  entities: {
    products: {
      label: 'Product',
      description: 'Product catalog records.',
      datasource: 'default',
      table: 'products',
      primaryKey: ['productCode'],
      fields: [
        'productCode',
        'productName',
        'productLine',
        'buyPrice',
        'quantityInStock',
      ],
      security: {
        read: { roles: ['GUEST', 'ADMIN'] },
      },
    },
    productlines: {
      label: 'Product Line',
      description: 'Product line categories.',
      datasource: 'default',
      table: 'productlines',
      primaryKey: ['productLine'],
      fields: ['productLine', 'textDescription'],
      security: {
        read: { roles: ['GUEST', 'ADMIN'] },
      },
    },
    customers: {
      label: 'Customer',
      description: 'Customer records from the secondary datasource.',
      datasource: 'secondary',
      table: 'customers',
      primaryKey: ['customerNumber'],
      fields: [
        'customerNumber',
        'customerName',
        'contactFirstName',
        'contactLastName',
        'city',
        'country',
      ],
      security: {
        read: { roles: ['ADMIN'] },
      },
    },
  },
}
