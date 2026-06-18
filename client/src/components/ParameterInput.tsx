import { DatePicker, Input, InputNumber, Select, Switch } from 'antd'
import type { ParameterDefinitionWithMetadata } from '../types/catalog'

interface ParameterInputProps {
  parameter: ParameterDefinitionWithMetadata
  value: string | number | boolean | null
  onChange: (value: string | number | boolean | null) => void
}

export function ParameterInput({ parameter, value, onChange }: ParameterInputProps) {
  const inputKind = parameter.input?.kind || 'text'

  switch (inputKind) {
    case 'number':
      return (
        <InputNumber
          style={{ width: '100%' }}
          placeholder={parameter.placeholder}
          value={value as number}
          onChange={(val) => onChange(val)}
          min={parameter.input?.min}
          max={parameter.input?.max}
          step={parameter.input?.step}
        />
      )

    case 'select':
      return (
        <Select
          style={{ width: '100%' }}
          placeholder={parameter.placeholder || `Select ${parameter.label || parameter.name}`}
          value={value as string}
          onChange={(val) => onChange(val)}
          options={parameter.input?.options?.map((opt) => ({ value: opt, label: opt })) || []}
          allowClear
        />
      )

    case 'boolean':
      return (
        <Switch
          checked={value as boolean}
          onChange={(checked) => onChange(checked)}
        />
      )

    case 'date':
      return (
        <DatePicker
          style={{ width: '100%' }}
          placeholder={parameter.placeholder}
          value={value ? undefined : undefined}
          onChange={(_date, dateString) => onChange(dateString as string)}
        />
      )

    case 'text':
    default:
      return (
        <Input
          placeholder={parameter.placeholder}
          value={value as string}
          onChange={(e) => onChange(e.target.value)}
        />
      )
  }
}
